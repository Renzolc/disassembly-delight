package dev.renzo.disassemblydelight.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/**
 * The Disassembly Table's own rules, shared by the table block and the Disassembly Table Upgrade:
 * planks to slabs, wooden slabs to a stick, beds to wool and planks, mob heads to spawn eggs, and Farmer's Delight
 * cutting-board recipes (matched by input only, every listed result, no chance rolls). Each consumes one item.
 */
public final class TableRules {
    /** Vanilla plank → matching wooden slab (1 plank → 2 slabs). */
    private static final Map<Item, Item> PLANK_TO_SLAB = createPlankToSlabMap();

    /** Bed → matching wool color (1 bed → 3 wool + 3 oak planks). */
    private static final Map<Item, Item> BED_TO_WOOL = createBedToWoolMap();

    /** Vanilla mob head → matching spawn egg (1 head → 1 egg). */
    private static final Map<Item, Item> MOB_HEAD_TO_SPAWN_EGG = createMobHeadToSpawnEggMap();

    private TableRules() {
    }

    /** Wood chain, bed, mob head, then cutting board, in that order. Results for one input item. */
    public static Optional<List<ItemStack>> resolve(Level level, ItemStack input) {
        Optional<List<ItemStack>> wood = resolveWoodChain(input);
        if (wood.isPresent()) {
            return wood;
        }
        Optional<List<ItemStack>> bed = resolveBed(input);
        if (bed.isPresent()) {
            return bed;
        }
        Optional<List<ItemStack>> mobHead = resolveMobHead(input);
        if (mobHead.isPresent()) {
            return mobHead;
        }
        return resolveCutting(level, input);
    }

    /** Planks → 2 matching slabs; wooden slabs → 1 stick. Not planks→sticks in one step. */
    public static Optional<List<ItemStack>> resolveWoodChain(ItemStack input) {
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

    public static Optional<List<ItemStack>> resolveBed(ItemStack input) {
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
    public static Optional<List<ItemStack>> resolveMobHead(ItemStack input) {
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
    public static Optional<List<ItemStack>> resolveCutting(Level level, ItemStack input) {
        if (level == null) {
            return Optional.empty();
        }
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
}
