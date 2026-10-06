package dev.renzo.disassemblydelight.contents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Combines a disassemble result with the input's stored contents (Rule C). Used by both the Disassembler block and the
 * Disassembler Upgrade so they behave the same:
 * <ul>
 * <li>contents come first in the output list, then the disassemble results;</li>
 * <li>an input whose contents cannot be read passes through unchanged (empty plan);</li>
 * <li>Create packages (contents-only tags) hand back their contents and are used up, with no recipe needed;
 * an empty or unreadable package passes through;</li>
 * <li>the caller commits all outputs or none.</li>
 * </ul>
 */
public final class ContainerDisassembly {
    private ContainerDisassembly() {
    }

    /** What the normal lookup (crafting, cutting, full_uncraft...) returned for the input. */
    public record Base(int consume, List<ItemStack> results) {
    }

    /**
     * What to insert and how many input items to use up. {@code afterCommit} must run once the outputs are in
     * (it clears Sophisticated saved-data inventories).
     */
    public record Plan(int consume, List<ItemStack> outputs, Runnable afterCommit) {
    }

    public static Optional<Plan> plan(Level level, ItemStack input, @Nullable Base base) {
        return plan(level, level.registryAccess(), input, base);
    }

    /** Empty means pass the input through unchanged. */
    public static Optional<Plan> plan(@Nullable Level level, HolderLookup.Provider registries, ItemStack input, @Nullable Base base) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        boolean contentsOnly = ContainerContents.isContentsOnly(input);
        if (base == null && !contentsOnly) {
            return Optional.empty();
        }
        Optional<ContainerContents.Extraction> extracted = ContainerContents.extract(level, registries, input);
        if (extracted.isEmpty()) {
            return Optional.empty();
        }
        ContainerContents.Extraction contents = extracted.get();

        if (contentsOnly) {
            if (contents.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Plan(1, splitToStackSize(contents.stacks()), contents.clearAfterInsert()));
        }

        int consume = base.consume();
        if (consume < 1 || input.getCount() < consume) {
            return Optional.empty();
        }
        // Saved-data contents belong to one item; never multiply them.
        if (contents.external() && consume != 1 && !contents.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> outputs = new ArrayList<>();
        // Every consumed item carries the same components (stacking requires it), so each one gives its contents.
        int copies = contents.external() ? 1 : consume;
        for (int i = 0; i < copies; i++) {
            outputs.addAll(splitToStackSize(contents.stacks()));
        }
        for (ItemStack result : base.results()) {
            if (result != null && !result.isEmpty()) {
                outputs.add(result.copy());
            }
        }
        if (outputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Plan(consume, outputs, contents.clearAfterInsert()));
    }

    /**
     * Inserts every stack into {@code target} (stacking first, like a hopper), or restores every slot and returns
     * false. Nothing is half-inserted.
     */
    public static boolean insertAllOrNothing(IItemHandlerModifiable target, List<ItemStack> outputs) {
        ItemStack[] before = new ItemStack[target.getSlots()];
        for (int i = 0; i < before.length; i++) {
            before[i] = target.getStackInSlot(i).copy();
        }
        for (ItemStack out : outputs) {
            if (out == null || out.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, out.copy(), false);
            if (!remainder.isEmpty()) {
                for (int i = 0; i < before.length; i++) {
                    target.setStackInSlot(i, before[i]);
                }
                return false;
            }
        }
        return true;
    }

    /** Splits oversized stacks (Sophisticated stack upgrades, toolboxes) into normal stacks. */
    public static List<ItemStack> splitToStackSize(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int max = Math.max(1, stack.getMaxStackSize());
            int remaining = stack.getCount();
            while (remaining > 0) {
                int take = Math.min(max, remaining);
                out.add(stack.copyWithCount(take));
                remaining -= take;
            }
        }
        return out;
    }
}
