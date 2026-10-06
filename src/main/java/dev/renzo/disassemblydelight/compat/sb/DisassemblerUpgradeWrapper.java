package dev.renzo.disassemblydelight.compat.sb;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.renzo.disassemblydelight.contents.ContainerDisassembly;
import dev.renzo.disassemblydelight.recipe.CraftUncraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.inventory.StatefulComponentItemHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;

/**
 * One input slot. Any crafting recipe (or this mod's full_uncraft) is reversed into the backpack.
 * An item with no disassemble result is moved into the backpack unchanged.
 */
public class DisassemblerUpgradeWrapper extends UpgradeWrapperBase<DisassemblerUpgradeWrapper, DisassemblerUpgradeItem>
        implements ITickableUpgrade {
    public static final int INPUT_SLOT = 0;

    private final StatefulComponentItemHandler inventory;
    private boolean processing;

    public DisassemblerUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
        inventory = new StatefulComponentItemHandler(upgrade, ModCoreDataComponents.LENIENT_CONTAINER.get(), 1) {
            @Override
            protected void onContentsChanged(int slot, ItemStack oldStack, ItemStack newStack) {
                super.onContentsChanged(slot, oldStack, newStack);
                save();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // Sophisticated Core syncs every slot with setStackInSlot, including air to clear it.
                // setStackInSlot throws if isItemValid is false, so empty stacks must be accepted.
                return stack.isEmpty() || super.isItemValid(slot, stack);
            }
        };
    }

    public StatefulComponentItemHandler getInventory() {
        return inventory;
    }

    @Override
    public boolean canBeDisabled() {
        return true;
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, BlockPos pos) {
        if (level.isClientSide || !isEnabled() || isInCooldown(level)) {
            return;
        }
        boolean worked = process(level, pos);
        setCooldown(level, worked ? 5 : 10);
    }

    public boolean process(Level level, BlockPos ignoredPos) {
        if (processing || level == null || level.isClientSide || !isEnabled()) {
            return false;
        }
        processing = true;
        try {
            return depositIntoBackpack(level);
        } catch (RuntimeException e) {
            // Runs inside the menu's broadcastChanges on the server tick; never let it take the world down.
            ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
            CraftUncraft.warnOnce("process:" + CraftUncraft.itemId(input),
                    "Disassembler Upgrade could not process " + CraftUncraft.itemId(input) + "; it was left in the input slot", e);
            return false;
        } finally {
            processing = false;
        }
    }

    private boolean depositIntoBackpack(Level level) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            return false;
        }
        Optional<Resolved> resolved = resolve(level);
        if (resolved.isEmpty()) {
            // Nothing to disassemble: move the item into the backpack unchanged so the input slot clears.
            return passThrough(input);
        }
        Resolved op = resolved.get();
        if (input.getCount() < op.consume()) {
            return false;
        }
        IItemHandler backpack = storageWrapper.getInventoryForUpgradeProcessing();
        // Contents first, then craft ingredients. All of it fits, or the input stays.
        if (backpack == null || !insertAll(backpack, op.results())) {
            return false;
        }
        op.clearContents().run();
        inventory.extractItem(INPUT_SLOT, op.consume(), false);
        return true;
    }

    /**
     * Moves an item with no disassemble result into the backpack as is. Whatever does not fit stays in the input.
     */
    private boolean passThrough(ItemStack input) {
        IItemHandler backpack = storageWrapper.getInventoryForUpgradeProcessing();
        if (backpack == null) {
            return false;
        }
        ItemStack leftover = InventoryHelper.insertIntoInventory(input.copy(), backpack, false);
        int moved = input.getCount() - (leftover == null ? 0 : leftover.getCount());
        if (moved <= 0) {
            return false;
        }
        inventory.extractItem(INPUT_SLOT, moved, false);
        return true;
    }

    /**
     * Commits through the backpack's own inserter. If any result does not fit, the inventory is restored
     * and the input is left alone.
     */
    private static boolean insertAll(IItemHandler backpack, List<ItemStack> results) {
        if (!(backpack instanceof IItemHandlerModifiable modifiable)) {
            return false;
        }
        ItemStack[] before = new ItemStack[backpack.getSlots()];
        for (int i = 0; i < before.length; i++) {
            before[i] = backpack.getStackInSlot(i).copy();
        }
        boolean fitted = true;
        for (ItemStack stack : results) {
            ItemStack leftover = InventoryHelper.insertIntoInventory(stack.copy(), backpack, false);
            if (!leftover.isEmpty()) {
                fitted = false;
                break;
            }
        }
        if (!fitted) {
            for (int i = 0; i < before.length; i++) {
                modifiable.setStackInSlot(i, before[i]);
            }
        }
        return fitted;
    }

    private Optional<Resolved> resolve(Level level) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        return resolveStack(level, input);
    }

    /** Client preview of what the next uncraft will insert. Not a container slot. */
    public List<ItemStack> preview(Level level) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        try {
            return resolveStack(level, input).map(Resolved::results).orElse(List.of());
        } catch (RuntimeException e) {
            CraftUncraft.warnOnce("preview:" + CraftUncraft.itemId(input),
                    "Disassembler Upgrade preview failed for " + CraftUncraft.itemId(input), e);
            return List.of();
        }
    }

    /**
     * Disassemble result plus the input's stored contents (shared with the Disassembler block). An input whose contents
     * cannot be read resolves to nothing, so it is moved into the backpack unchanged.
     */
    private static Optional<Resolved> resolveStack(Level level, ItemStack input) {
        if (input.isEmpty() || level == null) {
            return Optional.empty();
        }
        ContainerDisassembly.Base base = CraftUncraft.resolve(level, input)
                .map(crafted -> new ContainerDisassembly.Base(crafted.consume(), crafted.results()))
                .orElse(null);
        return ContainerDisassembly.plan(level, input, base)
                .map(plan -> new Resolved(plan.consume(), plan.outputs(), plan.afterCommit()));
    }

    private record Resolved(int consume, List<ItemStack> results, Runnable clearContents) {}
}
