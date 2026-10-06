package dev.renzo.disassemblydelight.compat.sb;

import dev.renzo.disassemblydelight.DisassemblyDelight;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

public final class DisassemblerUpgradeSetup {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DisassemblyDelight.MOD_ID);

    public static final DeferredHolder<Item, DisassemblerUpgradeItem> DISASSEMBLER_UPGRADE =
            ITEMS.register("disassembler_upgrade", DisassemblerUpgradeItem::new);

    public static final UpgradeContainerType<DisassemblerUpgradeWrapper, DisassemblerUpgradeContainer> CONTAINER =
            new UpgradeContainerType<>(DisassemblerUpgradeContainer::new);

    private DisassemblerUpgradeSetup() {}

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(DisassemblerUpgradeSetup::registerContainers);
        DisassemblyDelight.LOGGER.info("Disassembler Upgrade registered");
    }

    private static void registerContainers(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.MENU)) {
            return;
        }
        UpgradeContainerRegistry.register(DISASSEMBLER_UPGRADE.getId(), CONTAINER);
    }
}
