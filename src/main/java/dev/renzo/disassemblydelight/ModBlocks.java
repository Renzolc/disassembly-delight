package dev.renzo.disassemblydelight;

import dev.renzo.disassemblydelight.block.DisassemblerBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DisassemblyDelight.MOD_ID);

    public static final DeferredBlock<Block> DISASSEMBLER = BLOCKS.register("disassembler",
            () -> new DisassemblerBlock(disassemblerProps()));

    private ModBlocks() {}

    /** Like a crafting table: breakable by hand; axe preferred via #minecraft:mineable/axe. */
    private static BlockBehaviour.Properties disassemblerProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }
}
