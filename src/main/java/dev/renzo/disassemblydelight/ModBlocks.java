package dev.renzo.disassemblydelight;

import dev.renzo.disassemblydelight.block.DisassemblerBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DisassemblyDelight.MOD_ID);

    public static final DeferredBlock<Block> DISASSEMBLER = BLOCKS.register("disassembler",
            () -> new DisassemblerBlock(disassemblerProps()));

    private ModBlocks() {}

    /**
     * Copper machine. Breakable by hand and always drops itself (no tool required);
     * pickaxe or axe mine it faster via #minecraft:mineable/pickaxe and #minecraft:mineable/axe.
     * noOcclusion: the model has a recessed window, a hollow hopper and a cog sticking out.
     */
    private static BlockBehaviour.Properties disassemblerProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(2.5F)
                .sound(SoundType.COPPER)
                .noOcclusion();
    }
}
