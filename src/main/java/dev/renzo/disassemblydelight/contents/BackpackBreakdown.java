package dev.renzo.disassemblydelight.contents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.renzo.disassemblydelight.recipe.CraftUncraft;
import dev.renzo.disassemblydelight.recipe.FullUncraftRecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Sophisticated Backpacks breakdown chain, shared by the Disassembly Table block and the Disassembly Table Upgrade.
 *
 * <p>Each backpack tier breaks down into the next-lower tier plus that tier's upgrade materials, as listed in this
 * mod's {@code full_uncraft} recipes (falling back to the crafting recipe when a backpack has none). The lower-tier
 * backpack keeps the contents, upgrades and settings; what it has no room for comes out next to it
 * ({@link TierTransfer}). The bottom of the chain (a backpack whose breakdown contains no backpack, i.e. the
 * regular {@code sophisticatedbackpacks:backpack}) hands back its full contents and upgrades plus its crafting
 * ingredients. Backpacks stored inside come out whole, never emptied.
 *
 * <p>This class touches no Sophisticated Backpacks classes itself, so it is safe to load without that mod.
 */
public final class BackpackBreakdown {
    private static final String SB = "sophisticatedbackpacks";

    private BackpackBreakdown() {
    }

    public static boolean isBackpack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && SophisticatedContents.isLoaded(SB)
                && dev.renzo.disassemblydelight.compat.sb.StoredContents.isBackpack(stack);
    }

    /** Empty means "pass the backpack through unchanged" (linked endpoint, no breakdown, unreadable contents). */
    public static Optional<ContainerDisassembly.Plan> plan(Level level, ItemStack input) {
        if (level == null || !isBackpack(input)) {
            return Optional.empty();
        }
        List<ItemStack> base = baseResults(level, input);
        if (base.isEmpty()) {
            return Optional.empty();
        }
        return dev.renzo.disassemblydelight.compat.sb.BackpackChain.plan(level, input, base);
    }

    /** The backpack's own breakdown: the full_uncraft recipe, else the crafting recipe. */
    public static List<ItemStack> baseResults(Level level, ItemStack input) {
        Optional<FullUncraftRecipe> recipe = FullUncraftRecipe.find(level, input);
        List<ItemStack> out = new ArrayList<>();
        if (recipe.isPresent()) {
            out.addAll(recipe.get().copyResults());
        } else {
            CraftUncraft.resolve(level, input).ifPresent(result -> {
                if (result.consume() == 1) {
                    result.results().forEach(stack -> out.add(stack.copy()));
                }
            });
        }
        out.removeIf(stack -> stack == null || stack.isEmpty());
        return out;
    }
}
