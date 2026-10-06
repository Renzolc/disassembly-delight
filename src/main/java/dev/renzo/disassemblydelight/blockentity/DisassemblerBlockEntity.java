package dev.renzo.disassemblydelight.blockentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import dev.renzo.disassemblydelight.ModBlockEntities;
import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.menu.DisassemblerMenu;
import dev.renzo.disassemblydelight.contents.BackpackBreakdown;
import dev.renzo.disassemblydelight.contents.ContainerContents;
import dev.renzo.disassemblydelight.contents.ContainerDisassembly;
import dev.renzo.disassemblydelight.recipe.CraftUncraft;
import dev.renzo.disassemblydelight.recipe.TableRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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

    /**
     * Results of a backpack breakdown that did not fit the nine output slots (a full backpack's contents). They move
     * into the outputs as room frees up, are saved with the block, and drop with it when it is broken. While anything
     * is pending, no new input is processed.
     */
    private final List<ItemStack> pending = new ArrayList<>();

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
        for (ItemStack stack : pending) {
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }
        return list;
    }

    /** Items waiting for room in the outputs (copies). */
    public List<ItemStack> getPending() {
        List<ItemStack> copy = new ArrayList<>(pending.size());
        pending.forEach(stack -> copy.add(stack.copy()));
        return copy;
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
        if (!be.pending.isEmpty()) {
            be.drainPending();
        }
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
        if (!pending.isEmpty()) {
            return; // finish handing out the last backpack first
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
        if (!isDisassembler && BackpackBreakdown.isBackpack(input)) {
            // Backpack chain: lower tier keeps the contents, or the regular backpack hands everything back.
            Optional<ContainerDisassembly.Plan> backpack = BackpackBreakdown.plan(level, input);
            if (backpack.isEmpty()) {
                passThrough(input);
            } else {
                commitWithOverflow(input, backpack.get());
            }
            return;
        }
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
        pending.addAll(op.runCommit());
        drainPending();
        setChanged();
    }

    /**
     * Backpack breakdowns always go ahead: what fits goes into the outputs, the rest waits in {@link #pending}.
     * Nothing is deleted, even when a full netherite backpack meets nine busy output slots.
     */
    private void commitWithOverflow(ItemStack input, ContainerDisassembly.Plan op) {
        resetShortStackWait();
        int consume = op.consume();
        if (consume <= 0 || input.getCount() < consume) {
            return;
        }
        List<ItemStack> outputs = new ArrayList<>(op.outputs());
        // The commit reads the backpack in the input slot, so it runs before the input is used up.
        outputs.addAll(op.runCommit());
        input.shrink(consume);
        items.setStackInSlot(INPUT_SLOT, input.isEmpty() ? ItemStack.EMPTY : input);
        RangedWrapper view = outputInsertView();
        for (ItemStack out : outputs) {
            if (out == null || out.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(view, out.copy(), false);
            if (!remainder.isEmpty()) {
                pending.add(remainder);
            }
        }
        setChanged();
    }

    private void drainPending() {
        if (pending.isEmpty()) {
            return;
        }
        RangedWrapper view = outputInsertView();
        boolean changed = false;
        for (int i = 0; i < pending.size(); i++) {
            ItemStack stack = pending.get(i);
            ItemStack remainder = stack.isEmpty() ? ItemStack.EMPTY : ItemHandlerHelper.insertItemStacked(view, stack.copy(), false);
            if (remainder.getCount() != stack.getCount()) {
                changed = true;
            }
            pending.set(i, remainder);
        }
        pending.removeIf(ItemStack::isEmpty);
        if (changed) {
            setChanged();
        }
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
        // Steps 1-5 are shared with the Disassembly Table Upgrade (TableRules).
        Optional<List<ItemStack>> rules = TableRules.resolve(level, input);
        if (rules.isPresent()) {
            return Optional.of(new ResolvedDisassemble(rules.get(), 1));
        }

        // Reverse-craft only: skip damaged tools/armor (would invent full ingredients unfairly).
        // Damaged knives etc. still reach the cutting path above for salvage recipes.
        if (input.isDamageableItem() && input.isDamaged()) {
            return Optional.empty();
        }

        // The Disassembly Table Upgrade comes apart into its recipe (table, 2 hoppers, upgrade base, 3 redstone).
        if (isDisassemblerUpgrade(input)) {
            return CraftUncraft.resolve(level, input).map(r -> new ResolvedDisassemble(r.results(), Math.max(1, r.consume())));
        }

        return findBestRecipe(input).map(holder -> {
            List<ItemStack> outs = ingredientsOf(holder.value());
            int consume = holder.value().getResultItem(level.registryAccess()).getCount();
            return new ResolvedDisassemble(outs, Math.max(1, consume));
        });
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
        if (!pending.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack stack : pending) {
                if (!stack.isEmpty()) {
                    list.add(stack.save(registries));
                }
            }
            tag.put("Pending", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        progress = tag.getInt("Progress");
        pending.clear();
        ListTag list = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(pending::add);
        }
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

    private static boolean isDisassemblerUpgrade(ItemStack input) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(input.getItem());
        return id != null && id.getNamespace().equals("disassembly_delight") && id.getPath().equals("disassembler_upgrade");
    }

    private record ResolvedDisassemble(List<ItemStack> outputs, int consumeCount) {}
}
