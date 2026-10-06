package dev.renzo.disassemblydelight.client;

import dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeContainer;
import dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeSetup;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;

public final class DisassemblerUpgradeClientSetup {
    private DisassemblerUpgradeClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(DisassemblerUpgradeClientSetup::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> UpgradeGuiManager.registerTab(DisassemblerUpgradeSetup.CONTAINER,
                (DisassemblerUpgradeContainer uc, Position p, StorageScreenBase<?> s) -> new DisassemblerUpgradeTab(uc, p, s)));
    }
}
