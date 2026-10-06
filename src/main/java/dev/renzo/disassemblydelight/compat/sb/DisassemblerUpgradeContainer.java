package dev.renzo.disassemblydelight.compat.sb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SlotSuppliedHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

public class DisassemblerUpgradeContainer extends UpgradeContainerBase<DisassemblerUpgradeWrapper, DisassemblerUpgradeContainer> {
    public DisassemblerUpgradeContainer(Player player, int upgradeContainerId, DisassemblerUpgradeWrapper upgradeWrapper,
            UpgradeContainerType<DisassemblerUpgradeWrapper, DisassemblerUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);
        // Only the input slot. Output slots used to sync air and crash StatefulComponentItemHandler.
        slots.add(new SlotSuppliedHandler(supplyFromWrapper(DisassemblerUpgradeWrapper::getInventory), DisassemblerUpgradeWrapper.INPUT_SLOT, -100, -100) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!player.level().isClientSide) {
                    upgradeWrapper.process(player.level(), player.blockPosition());
                }
            }
        });
    }

    @Override
    public void handlePacket(CompoundTag data) {
    }
}
