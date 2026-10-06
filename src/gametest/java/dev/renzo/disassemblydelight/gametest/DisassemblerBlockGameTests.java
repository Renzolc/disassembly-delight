package dev.renzo.disassemblydelight.gametest;

import dev.renzo.disassemblydelight.DisassemblyDelight;
import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.block.DisassemblerBlock;
import dev.renzo.disassemblydelight.blockentity.DisassemblerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The copper table block: it faces a direction, rotates, keeps its block entity, and drops itself without a tool. */
@GameTestHolder(DisassemblyDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public class DisassemblerBlockGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void tableFacesRotatesAndDropsItself(GameTestHelper helper) {
        BlockState state = ModBlocks.DISASSEMBLER.get().defaultBlockState().setValue(DisassemblerBlock.FACING, Direction.EAST);
        helper.setBlock(POS, state);
        helper.assertTrue(helper.getBlockEntity(POS) instanceof DisassemblerBlockEntity, "block entity must exist for every facing");
        helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(DisassemblerBlock.FACING) == Direction.SOUTH, "rotate turns the front");
        helper.assertTrue(!state.requiresCorrectToolForDrops(), "the table must drop without a tool");
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true); // breaking by hand, loot table drops
        helper.succeedWhen(() -> helper.assertItemEntityPresent(ModBlocks.DISASSEMBLER.get().asItem(), POS, 2.0));
    }
}
