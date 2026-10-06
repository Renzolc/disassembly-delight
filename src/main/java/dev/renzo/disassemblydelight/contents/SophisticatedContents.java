package dev.renzo.disassemblydelight.contents;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Sophisticated Backpacks / Storage keep a container's items in saved data keyed by the stack's storage UUID,
 * so the generic component readers cannot see them. Backpacks go through {@code compat.sb.StoredContents}
 * (only touched when Sophisticated Backpacks is loaded); storage blocks through reflection.
 *
 * <p>Storage items are recognised by class ({@code StorageBlockItem}), not namespace, so add-ons such as the
 * Sophisticated Emerald Upgrade chests and barrels are emptied too, and Sophisticated Storage upgrades (which
 * are not storage blocks) fall through to the normal component readers.
 */
final class SophisticatedContents {
    private static final String STORAGE_BLOCK_ITEM = "net.p3pp3rf1y.sophisticatedstorage.item.StorageBlockItem";
    private static final String STACK_STORAGE_WRAPPER = "net.p3pp3rf1y.sophisticatedstorage.item.StackStorageWrapper";

    private static volatile Class<?> storageBlockItemClass;
    private static volatile boolean storageLookupDone;

    private SophisticatedContents() {
    }

    static boolean isStorageItem(ItemStack stack) {
        if (isLoaded("sophisticatedbackpacks") && dev.renzo.disassemblydelight.compat.sb.StoredContents.isBackpack(stack)) {
            return true;
        }
        Class<?> storageItem = storageBlockItemClass();
        return storageItem != null && storageItem.isInstance(stack.getItem());
    }

    static Optional<ContainerContents.Extraction> extract(Level level, ItemStack stack) {
        if (isLoaded("sophisticatedbackpacks") && dev.renzo.disassemblydelight.compat.sb.StoredContents.isBackpack(stack)) {
            return dev.renzo.disassemblydelight.compat.sb.StoredContents.backpack(stack);
        }
        return storage(level, stack);
    }

    private static Class<?> storageBlockItemClass() {
        if (!storageLookupDone) {
            synchronized (SophisticatedContents.class) {
                if (!storageLookupDone) {
                    if (isLoaded("sophisticatedstorage")) {
                        try {
                            storageBlockItemClass = Class.forName(STORAGE_BLOCK_ITEM);
                        } catch (ClassNotFoundException | LinkageError e) {
                            storageBlockItemClass = null;
                        }
                    }
                    storageLookupDone = true;
                }
            }
        }
        return storageBlockItemClass;
    }

    private static Optional<ContainerContents.Extraction> storage(Level level, ItemStack stack) {
        try {
            Class<?> type = Class.forName(STACK_STORAGE_WRAPPER);
            Method fromStack = type.getMethod("fromStack", net.minecraft.core.HolderLookup.Provider.class, ItemStack.class);
            Object wrapper = fromStack.invoke(null, level.registryAccess(), stack);
            if (!((boolean) type.getMethod("hasContents").invoke(wrapper))) {
                return Optional.of(ContainerContents.Extraction.none());
            }
            if (holdsFluid(wrapper)) {
                return Optional.empty();
            }
            IItemHandler inventory = (IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
            IItemHandler upgrades = (IItemHandler) wrapper.getClass().getMethod("getUpgradeHandler").invoke(wrapper);
            List<ItemStack> stacks = new ArrayList<>();
            ContainerContents.copySlots(inventory, stacks);
            ContainerContents.copySlots(upgrades, stacks);
            return Optional.of(new ContainerContents.Extraction(stacks, () -> {
                clear(inventory);
                clear(upgrades);
            }, true));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            ContainerContents.warnOnce("storage:" + ContainerContents.itemId(stack),
                    "Disassembler left a storage item alone; its contents could not be read", e);
            return Optional.empty();
        }
    }

    private static boolean holdsFluid(Object wrapper) {
        try {
            Object optional = wrapper.getClass().getMethod("getFluidHandler").invoke(wrapper);
            if (!(optional instanceof Optional<?> fluids) || fluids.isEmpty()) {
                return false;
            }
            if (!(fluids.get() instanceof IFluidHandler handler)) {
                return true;
            }
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                if (!handler.getFluidInTank(tank).isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    static void clear(IItemHandler handler) {
        if (!(handler instanceof IItemHandlerModifiable modifiable)) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    static boolean isLoaded(String modId) {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(modId);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
