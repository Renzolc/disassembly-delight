package dev.renzo.disassemblydelight.blockentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import dev.renzo.disassemblydelight.ModBlockEntities;
import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.menu.DisassemblerMenu;
import dev.renzo.disassemblydelight.contents.ContainerContents;
import dev.renzo.disassemblydelight.contents.ContainerDisassembly;
import dev.renzo.disassemblydelight.recipe.CraftUncraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/**
 * Hopper-fed auto cutting board (+ wood breakdown + bed uncraft + reverse-craft fallback).
 * Items with no disassemble result pass through to the output slots unchanged.
 * Tools are built into the machine craft recipe — cutting matches by input ingredient only.
 */
public class DisassemblerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOTS = 9;
    public static final int TOTAL_SLOTS = 1 + OUTPUT_SLOTS;
    public static final int PROCESS_INTERVAL = 20; // 1 second
    /**
     * Process cycles to wait for more items when a recipe needs a bigger stack (e.g. 4 torches) before
     * passing the short stack through unchanged. Hoppers add items every cycle while they are feeding.
     */
    public static final int SHORT_STACK_WAIT_CYCLES = 5;

    /** Vanilla plank → matching wooden slab (1 plank → 2 slabs). */
    private static final Map<Item, Item> PLANK_TO_SLAB = createPlankToSlabMap();

    /** Bed → matching wool color (1 bed → 3 wool + 3 oak planks). */
    private static final Map<Item, Item> BED_TO_WOOL = createBedToWoolMap();

    /** Vanilla mob head → matching spawn egg (1 head → 1 egg). */
    private static final Map<Item, Item> MOB_HEAD_TO_SPAWN_EGG = createMobHeadToSpawnEggMap();

    private final ItemStackHandler items = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty();
        }
    };

    /** Top/sides: insert into input only; no extract. */
    private final IItemHandler inputHandler = new RangedWrapper(items, INPUT_SLOT, INPUT_SLOT + 1) {
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    };

    /** Bottom: extract from outputs only; no insert. */
    private final IItemHandler outputHandler = new RangedWrapper(items, INPUT_SLOT + 1, TOTAL_SLOTS) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    private int progress;
    private int shortStackCount = -1;
    private int shortStackCycles;

    public DisassemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISASSEMBLER.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public IItemHandler getHandlerForSide(@Nullable Direction side) {
        if (side == null) {
            return items;
        }
        if (side == Direction.DOWN) {
            return outputHandler;
        }
        return inputHandler;
    }

    public NonNullList<ItemStack> getDrops() {
        NonNullList<ItemStack> list = NonNullList.create();
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }
        return list;
    }

    public int getRedstoneSignal() {
        int filled = 0;
        int total = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            total += items.getSlotLimit(i);
            filled += stack.getCount();
        }
        if (total == 0) {
            return 0;
        }
        return (int) Math.floor(1 + (14.0 * filled) / total);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DisassemblerBlockEntity be) {
        be.progress++;
        if (be.progress < PROCESS_INTERVAL) {
            return;
        }
        be.progress = 0;
        be.tryDisassemble();
    }

    private void tryDisassemble() {
        if (level == null || level.isClientSide) {
            return;
        }
        ItemStack input = items.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            resetShortStackWait();
            return;
        }
        try {
            disassembleOrPassThrough(input);
        } catch (RuntimeException e) {
            // A bad recipe or odd item must not crash the server tick. Treat it as not disassemblable.
            CraftUncraft.warnOnce("disassembler:" + CraftUncraft.itemId(input),
                    "Disassembler could not disassemble " + CraftUncraft.itemId(input) + "; passing it through unchanged", e);
            try {
                passThrough(items.getStackInSlot(INPUT_SLOT));
            } catch (RuntimeException ignored) {
                // Leave the item in the input slot.
            }
        }
    }

    private void disassembleOrPassThrough(ItemStack input) {
        // Never disassemble the Disassembler itself; it passes through like any other non-disassemblable item.
        boolean isDisassembler = input.is(ModBlocks.DISASSEMBLER.asItem());
        Optional<ResolvedDisassemble> resolved = isDisassembler || ContainerContents.isForcedPassThrough(input)
                ? Optional.empty()
                : resolveDisassemble(input);
        ContainerDisassembly.Base base = resolved
                .filter(op -> op.outputs() != null && !op.outputs().isEmpty())
                .map(op -> new ContainerDisassembly.Base(op.consumeCount(), op.outputs()))
                .orElse(null);
        // Stored contents come back first, with the disassemble results (Rule C). Unreadable contents → pass through.
        Optional<ContainerDisassembly.Plan> plan = isDisassembler
                ? Optional.empty()
                : ContainerDisassembly.plan(level, input, base);
        if (plan.isEmpty()) {
            if (resolved.isEmpty() && !isDisassembler && waitingForMoreInput(input)) {
                return;
            }
            passThrough(input);
            return;
        }
        resetShortStackWait();
        ContainerDisassembly.Plan op = plan.get();
        List<ItemStack> outputs = op.outputs();
        int consume = op.consume();
        if (consume <= 0 || input.getCount() < consume) {
            return;
        }
        if (!fits(outputs, true)) {
            // Even nine empty output slots could not hold contents + results (a full shulker box):
            // keep the container whole and move it on instead of blocking the input forever.
            passThrough(input);
            return;
        }
        if (!fits(outputs, false)) {
            return;
        }
        if (!ContainerDisassembly.insertAllOrNothing(outputInsertView(), outputs)) {
            return;
        }
        input.shrink(consume);
        items.setStackInSlot(INPUT_SLOT, input.isEmpty() ? ItemStack.EMPTY : input);
        op.afterCommit().run();
        setChanged();
    }

    /**
     * Moves an item with no disassemble result to the output slots unchanged so hoppers and pipes carry it on.
     * Whatever does not fit stays in the input until the outputs drain.
     */
    private void passThrough(ItemStack input) {
        resetShortStackWait();
        if (input.isEmpty()) {
            return;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(outputInsertView(), input.copy(), false);
        if (remainder.getCount() == input.getCount()) {
            return;
        }
        items.setStackInSlot(INPUT_SLOT, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        setChanged();
    }

    /**
     * True while a reverse-craft recipe exists that needs more items than the input holds (a 4-torch craft
     * with 2 torches in the slot) and the stack is still growing. After {@link #SHORT_STACK_WAIT_CYCLES}
     * cycles with no new items, the short stack passes through instead of sitting in the input forever.
     */
    private boolean waitingForMoreInput(ItemStack input) {
        if (!couldDisassembleWithMore(input)) {
            resetShortStackWait();
            return false;
        }
        if (input.getCount() != shortStackCount) {
            shortStackCount = input.getCount();
            shortStackCycles = 0;
            return true;
        }
        shortStackCycles++;
        return shortStackCycles < SHORT_STACK_WAIT_CYCLES;
    }

    private void resetShortStackWait() {
        shortStackCount = -1;
        shortStackCycles = 0;
    }

    private boolean couldDisassembleWithMore(ItemStack input) {
        if ((input.isDamageableItem() && input.isDamaged()) || isDisassemblerUpgrade(input)) {
            return false;
        }
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            try {
                CraftingRecipe recipe = holder.value();
                if (recipe == null || recipe.isSpecial()) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (result == null || result.isEmpty() || result.getItem() != input.getItem()
                        || result.getCount() <= input.getCount() || result.getCount() > input.getMaxStackSize()) {
                    continue;
                }
                NonNullList<Ingredient> ingredients = recipe.getIngredients();
                if (ingredients == null || ingredients.isEmpty() || hasUnsafeRemainingItems(ingredients)) {
                    continue;
                }
                if (!ingredientsOf(recipe).isEmpty()) {
                    return true;
                }
            } catch (RuntimeException e) {
                CraftUncraft.warnOnce("recipe:" + holder.id(), "Disassembler skipped crafting recipe " + holder.id() + " (it could not be read)", e);
            }
        }
        return false;
    }

    /**
     * Priority:
     * 1) plank → wooden slabs
     * 2) wooden slab → stick
     * 3) bed → 3 matching wool + 3 oak planks
     * 4) mob heads → matching spawn eggs
     * 5) Farmer's Delight cutting-board recipes (input match only; tools built into machine)
     * 6) reverse crafting fallback (skips damaged tools/armor)
     */
    private Optional<ResolvedDisassemble> resolveDisassemble(ItemStack input) {
        Optional<List<ItemStack>> wood = resolveWoodChain(input);
        if (wood.isPresent()) {
            return Optional.of(new ResolvedDisassemble(wood.get(), 1));
        }

        Optional<List<ItemStack>> bed = resolveBed(input);
        if (bed.isPresent()) {
            return Optional.of(new ResolvedDisassemble(bed.get(), 1));
        }

        Optional<List<ItemStack>> mobHead = resolveMobHead(input);
        if (mobHead.isPresent()) {
            return Optional.of(new ResolvedDisassemble(mobHead.get(), 1));
        }

        Optional<List<ItemStack>> cutting = resolveCutting(input);
        if (cutting.isPresent()) {
            return Optional.of(new ResolvedDisassemble(cutting.get(), 1));
        }

        // Reverse-craft only: skip damaged tools/armor (would invent full ingredients unfairly).
        // Damaged knives etc. still reach the cutting path above for salvage recipes.
        if (input.isDamageableItem() && input.isDamaged()) {
            return Optional.empty();
        }

        // Disassembler Upgrade full-returns only through the backpack upgrade, never this block.
        if (isDisassemblerUpgrade(input)) {
            return Optional.empty();
        }

        return findBestRecipe(input).map(holder -> {
            List<ItemStack> outs = ingredientsOf(holder.value());
            int consume = holder.value().getResultItem(level.registryAccess()).getCount();
            return new ResolvedDisassemble(outs, Math.max(1, consume));
        });
    }

    /** Planks → 2 matching slabs; wooden slabs → 1 stick. Not planks→sticks in one step. */
    private Optional<List<ItemStack>> resolveWoodChain(ItemStack input) {
        if (input.is(ItemTags.PLANKS)) {
            Item slab = PLANK_TO_SLAB.get(input.getItem());
            if (slab == null) {
                slab = lookupSlabForPlank(input.getItem());
            }
            if (slab != null && slab != Items.AIR) {
                return Optional.of(List.of(new ItemStack(slab, 2)));
            }
            return Optional.empty();
        }
        if (input.is(ItemTags.WOODEN_SLABS)) {
            return Optional.of(List.of(new ItemStack(Items.STICK, 1)));
        }
        return Optional.empty();
    }

    private Optional<List<ItemStack>> resolveBed(ItemStack input) {
        Item wool = BED_TO_WOOL.get(input.getItem());
        if (wool == null) {
            return Optional.empty();
        }
        // Explicit special-case matching vanilla bed recipe (3 wool + 3 oak planks).
        return Optional.of(List.of(
                new ItemStack(wool, 3),
                new ItemStack(Items.OAK_PLANKS, 3)
        ));
    }

    /** Mob heads → matching spawn eggs, with a same-namespace fallback for modded heads. */
    private Optional<List<ItemStack>> resolveMobHead(ItemStack input) {
        Item spawnEgg = MOB_HEAD_TO_SPAWN_EGG.get(input.getItem());
        if (spawnEgg != null && spawnEgg != Items.AIR) {
            return Optional.of(List.of(new ItemStack(spawnEgg, 1)));
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(input.getItem());
        if (id == null || id.getPath().equals("player_head")) {
            return Optional.empty();
        }
        String path = id.getPath();
        String suffix;
        if (path.endsWith("_head")) {
            suffix = "_head";
        } else if (path.endsWith("_skull")) {
            suffix = "_skull";
        } else {
            return Optional.empty();
        }
        String base = path.substring(0, path.length() - suffix.length());
        if (base.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation spawnEggId = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), base + "_spawn_egg"
        );
        spawnEgg = BuiltInRegistries.ITEM.get(spawnEggId);
        if (spawnEgg == Items.AIR) {
            return Optional.empty();
        }
        return Optional.of(List.of(new ItemStack(spawnEgg, 1)));
    }

    /**
     * Match any farmersdelight:cutting recipe by input ingredient only (ignore tool).
     * Uses {@link CuttingBoardRecipe#getResults()} so automation always receives the listed
     * stacks (chance rolls are ignored — deterministic full outputs for the auto machine).
     * Consumes 1 input item per op.
     */
    private Optional<List<ItemStack>> resolveCutting(ItemStack input) {
        List<ItemStack> best = null;
        ResourceLocation bestId = null;
        for (RecipeHolder<CuttingBoardRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
            try {
                CuttingBoardRecipe recipe = holder.value();
                NonNullList<Ingredient> ingredients = recipe.getIngredients();
                if (ingredients == null || ingredients.isEmpty() || ingredients.getFirst() == null || !ingredients.getFirst().test(input)) {
                    continue;
                }
                List<ItemStack> results = new ArrayList<>();
                for (ItemStack stack : recipe.getResults()) {
                    if (stack != null && !stack.isEmpty()) {
                        results.add(stack.copy());
                    }
                }
                if (results.isEmpty()) {
                    continue;
                }
                ResourceLocation id = holder.id();
                if (best == null || id.toString().compareTo(bestId.toString()) < 0) {
                    best = results;
                    bestId = id;
                }
            } catch (RuntimeException e) {
                CraftUncraft.warnOnce("recipe:" + holder.id(), "Disassembler skipped cutting recipe " + holder.id() + " (it could not be read)", e);
            }
        }
        return best == null ? Optional.empty() : Optional.of(best);
    }

    private Optional<RecipeHolder<CraftingRecipe>> findBestRecipe(ItemStack input) {
        List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            try {
                CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial()) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (result == null || result.isEmpty() || result.getItem() != input.getItem()) {
                    continue;
                }
                if (input.getCount() < result.getCount()) {
                    continue;
                }
                NonNullList<Ingredient> ingredients = recipe.getIngredients();
                if (ingredients.isEmpty()) {
                    continue;
                }
                if (hasUnsafeRemainingItems(ingredients)) {
                    continue;
                }
                List<ItemStack> resolved = ingredientsOf(recipe);
                if (resolved.isEmpty()) {
                    continue;
                }
                matches.add(holder);
            } catch (RuntimeException e) {
                CraftUncraft.warnOnce("recipe:" + holder.id(), "Disassembler skipped crafting recipe " + holder.id() + " (it could not be read)", e);
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        matches.sort(Comparator
                .comparingInt((RecipeHolder<CraftingRecipe> h) -> countIngredients(h.value()))
                .thenComparing(h -> h.id().toString()));
        return Optional.of(matches.getFirst());
    }

    private static int countIngredients(CraftingRecipe recipe) {
        int n = 0;
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static boolean hasUnsafeRemainingItems(NonNullList<Ingredient> ingredients) {
        for (Ingredient ing : ingredients) {
            if (ing == null || ing.isEmpty()) {
                continue;
            }
            ItemStack[] stacks = ing.getItems();
            if (stacks == null || stacks.length == 0) {
                return true;
            }
            boolean allRemain = true;
            for (ItemStack s : stacks) {
                if (!s.hasCraftingRemainingItem()) {
                    allRemain = false;
                    break;
                }
            }
            if (allRemain) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> ingredientsOf(CraftingRecipe recipe) {
        List<ItemStack> out = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing == null || ing.isEmpty()) {
                continue;
            }
            ItemStack[] options = ing.getItems();
            if (options == null || options.length == 0) {
                return List.of();
            }
            ItemStack chosen = options[0];
            for (ItemStack opt : options) {
                if (!opt.hasCraftingRemainingItem()) {
                    chosen = opt;
                    break;
                }
            }
            ItemStack stack = chosen.copy();
            stack.setCount(1);
            boolean merged = false;
            for (ItemStack existing : out) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(1);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                out.add(stack);
            }
        }
        return out;
    }

    private boolean fits(List<ItemStack> outputs, boolean assumeEmptyOutputs) {
        ItemStack[] sim = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            sim[i] = assumeEmptyOutputs ? ItemStack.EMPTY : items.getStackInSlot(INPUT_SLOT + 1 + i).copy();
        }
        for (ItemStack out : outputs) {
            ItemStack remaining = out.copy();
            for (int i = 0; i < OUTPUT_SLOTS && !remaining.isEmpty(); i++) {
                if (!sim[i].isEmpty() && ItemStack.isSameItemSameComponents(sim[i], remaining)) {
                    int space = Math.min(sim[i].getMaxStackSize(), items.getSlotLimit(INPUT_SLOT + 1 + i)) - sim[i].getCount();
                    if (space > 0) {
                        int move = Math.min(space, remaining.getCount());
                        sim[i].grow(move);
                        remaining.shrink(move);
                    }
                }
            }
            for (int i = 0; i < OUTPUT_SLOTS && !remaining.isEmpty(); i++) {
                if (sim[i].isEmpty()) {
                    int move = Math.min(remaining.getMaxStackSize(), remaining.getCount());
                    sim[i] = remaining.copyWithCount(move);
                    remaining.shrink(move);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private RangedWrapper outputInsertView() {
        return new RangedWrapper(items, INPUT_SLOT + 1, TOTAL_SLOTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        progress = tag.getInt("Progress");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.disassembly_delight.disassembler");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DisassemblerMenu(containerId, playerInventory, this);
    }

    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    /** Fallback for modded planks: same namespace, path {@code *_planks} → {@code *_slab}. */
    @Nullable
    private static Item lookupSlabForPlank(Item plank) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(plank);
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (!path.endsWith("_planks")) {
            return null;
        }
        ResourceLocation slabId = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                path.substring(0, path.length() - "_planks".length()) + "_slab"
        );
        Item slab = BuiltInRegistries.ITEM.get(slabId);
        return slab == Items.AIR ? null : slab;
    }

    private static Map<Item, Item> createPlankToSlabMap() {
        Map<Item, Item> map = new HashMap<>();
        map.put(Items.OAK_PLANKS, Items.OAK_SLAB);
        map.put(Items.SPRUCE_PLANKS, Items.SPRUCE_SLAB);
        map.put(Items.BIRCH_PLANKS, Items.BIRCH_SLAB);
        map.put(Items.JUNGLE_PLANKS, Items.JUNGLE_SLAB);
        map.put(Items.ACACIA_PLANKS, Items.ACACIA_SLAB);
        map.put(Items.DARK_OAK_PLANKS, Items.DARK_OAK_SLAB);
        map.put(Items.MANGROVE_PLANKS, Items.MANGROVE_SLAB);
        map.put(Items.CHERRY_PLANKS, Items.CHERRY_SLAB);
        map.put(Items.BAMBOO_PLANKS, Items.BAMBOO_SLAB);
        map.put(Items.CRIMSON_PLANKS, Items.CRIMSON_SLAB);
        map.put(Items.WARPED_PLANKS, Items.WARPED_SLAB);
        return Map.copyOf(map);
    }

    private static Map<Item, Item> createBedToWoolMap() {
        Map<Item, Item> map = new HashMap<>();
        map.put(Items.WHITE_BED, Items.WHITE_WOOL);
        map.put(Items.ORANGE_BED, Items.ORANGE_WOOL);
        map.put(Items.MAGENTA_BED, Items.MAGENTA_WOOL);
        map.put(Items.LIGHT_BLUE_BED, Items.LIGHT_BLUE_WOOL);
        map.put(Items.YELLOW_BED, Items.YELLOW_WOOL);
        map.put(Items.LIME_BED, Items.LIME_WOOL);
        map.put(Items.PINK_BED, Items.PINK_WOOL);
        map.put(Items.GRAY_BED, Items.GRAY_WOOL);
        map.put(Items.LIGHT_GRAY_BED, Items.LIGHT_GRAY_WOOL);
        map.put(Items.CYAN_BED, Items.CYAN_WOOL);
        map.put(Items.PURPLE_BED, Items.PURPLE_WOOL);
        map.put(Items.BLUE_BED, Items.BLUE_WOOL);
        map.put(Items.BROWN_BED, Items.BROWN_WOOL);
        map.put(Items.GREEN_BED, Items.GREEN_WOOL);
        map.put(Items.RED_BED, Items.RED_WOOL);
        map.put(Items.BLACK_BED, Items.BLACK_WOOL);
        return Map.copyOf(map);
    }

    private static Map<Item, Item> createMobHeadToSpawnEggMap() {
        Map<Item, Item> map = new HashMap<>();
        map.put(Items.SKELETON_SKULL, Items.SKELETON_SPAWN_EGG);
        map.put(Items.WITHER_SKELETON_SKULL, Items.WITHER_SKELETON_SPAWN_EGG);
        map.put(Items.ZOMBIE_HEAD, Items.ZOMBIE_SPAWN_EGG);
        map.put(Items.CREEPER_HEAD, Items.CREEPER_SPAWN_EGG);
        map.put(Items.PIGLIN_HEAD, Items.PIGLIN_SPAWN_EGG);
        map.put(Items.DRAGON_HEAD, Items.ENDER_DRAGON_SPAWN_EGG);
        return Map.copyOf(map);
    }

    private static boolean isDisassemblerUpgrade(ItemStack input) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(input.getItem());
        return id != null && id.getNamespace().equals("disassembly_delight") && id.getPath().equals("disassembler_upgrade");
    }

    private record ResolvedDisassemble(List<ItemStack> outputs, int consumeCount) {}
}
