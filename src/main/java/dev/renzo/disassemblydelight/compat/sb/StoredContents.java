package dev.renzo.disassemblydelight.compat.sb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.renzo.disassemblydelight.contents.ContainerContents;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageFluidHandler;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointStackState;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageStackLifecycle;

/**
 * Sophisticated Backpacks side of container emptying. Only called when Sophisticated Backpacks is loaded.
 * Everything else (vanilla, Create, Supplementaries, Sophisticated Storage...) is in
 * {@link dev.renzo.disassemblydelight.contents.ContainerContents}.
 * Empty means "do not disassemble": the contents could not be read safely.
 */
public final class StoredContents {
    private StoredContents() {
    }

    public static boolean isBackpack(ItemStack stack) {
        return stack.getItem() instanceof BackpackItem;
    }

    public static Optional<ContainerContents.Extraction> backpack(ItemStack stack) {
        // A linked endpoint is a pointer at another backpack. Disassembling it would take that backpack's items.
        if (LinkedStorageStackLifecycle.classifyEndpoint(stack) == LinkedStorageEndpointStackState.ENDPOINT) {
            return Optional.empty();
        }
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(stack);
        if (holdsFluid(wrapper.getFluidHandler())) {
            return Optional.empty();
        }
        List<ItemStack> stacks = new ArrayList<>();
        copySlots(wrapper.getInventoryHandler(), stacks);
        copySlots(wrapper.getUpgradeHandler(), stacks);
        return Optional.of(new ContainerContents.Extraction(stacks, () -> {
            clear(wrapper.getInventoryHandler());
            clear(wrapper.getUpgradeHandler());
        }, true));
    }

    private static boolean holdsFluid(Optional<IStorageFluidHandler> fluid) {
        if (fluid.isEmpty()) {
            return false;
        }
        IStorageFluidHandler handler = fluid.get();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.getFluidInTank(tank).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void copySlots(IItemHandler handler, List<ItemStack> into) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                into.add(stack.copy());
            }
        }
    }

    private static void clear(IItemHandler handler) {
        if (!(handler instanceof IItemHandlerModifiable modifiable)) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
