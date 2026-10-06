package dev.renzo.disassemblydelight.mixin.sb;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeContainer;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;

/**
 * Shift-clicking an item from the player's inventory while the Disassembly Table Upgrade tab is open sends it into
 * the upgrade's input slot, which takes it apart into the backpack.
 *
 * <p>Sophisticated Core only tries an open tab after the backpack's own storage unless the player turned on
 * "shift-click into open tab first", so by default a shift-clicked item went straight into the backpack whole and the
 * upgrade never saw it. For this one tab, the open-tab-first order is used (extra slots, the open tab, upgrade slots,
 * storage), the same order Sophisticated Core uses when that setting is on. Upgrade items keep the normal order so
 * shift-clicking an upgrade still installs or stores it instead of taking it apart. Other tabs are untouched.
 */
@Mixin(value = StorageContainerMenuBase.class, remap = false)
public abstract class StorageContainerMenuBaseMixin {
    @Shadow
    protected abstract boolean isUpgradeSlot(int index);

    @Shadow
    public abstract boolean isStorageInventorySlot(int index);

    @Shadow
    protected abstract boolean isUpgradeSettingsSlot(int index);

    @Shadow
    public abstract Optional<UpgradeContainerBase<?, ?>> getOpenContainer();

    @Shadow
    private boolean isExtraSlot(int slotIndex) {
        throw new AssertionError();
    }

    @Shadow
    private boolean mergeStackToExtraSlots(Slot slot, ItemStack slotStack) {
        throw new AssertionError();
    }

    @Shadow
    private boolean mergeStackToOpenUpgradeTab(Slot sourceSlot, ItemStack slotStack) {
        throw new AssertionError();
    }

    @Shadow
    private boolean mergeStackToUpgradeSlots(Slot sourceSlot, ItemStack slotStack) {
        throw new AssertionError();
    }

    @Shadow
    private boolean mergeStackToStorage(Slot slot, ItemStack slotStack) {
        throw new AssertionError();
    }

    @Inject(method = "mergeSlotStack", at = @At("HEAD"), cancellable = true, require = 0)
    private void disassembly_delight$shiftClickIntoDisassemblyTab(Slot slot, int index, ItemStack slotStack, CallbackInfoReturnable<Boolean> cir) {
        if (slotStack.isEmpty() || slotStack.getItem() instanceof IUpgradeItem<?>) {
            return;
        }
        // Only slots of the player's own inventory; the storage, upgrade, settings and extra slots keep their order.
        if (isUpgradeSlot(index) || isStorageInventorySlot(index) || isUpgradeSettingsSlot(index) || isExtraSlot(index)) {
            return;
        }
        if (!(getOpenContainer().orElse(null) instanceof DisassemblerUpgradeContainer)) {
            return;
        }
        cir.setReturnValue(mergeStackToExtraSlots(slot, slotStack) || mergeStackToOpenUpgradeTab(slot, slotStack)
                || mergeStackToUpgradeSlots(slot, slotStack) || mergeStackToStorage(slot, slotStack));
    }
}
