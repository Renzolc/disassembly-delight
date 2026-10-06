package dev.renzo.disassemblydelight;

import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DisassemblyDelight.MOD_ID);

    static {
        ITEMS.registerSimpleBlockItem(ModBlocks.DISASSEMBLER);
    }

    private ModItems() {}
}
