package dev.renzo.disassemblydelight;

import java.util.function.Supplier;

import dev.renzo.disassemblydelight.blockentity.DisassemblerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DisassemblyDelight.MOD_ID);

    public static final Supplier<BlockEntityType<DisassemblerBlockEntity>> DISASSEMBLER = BLOCK_ENTITIES.register(
            "disassembler",
            () -> BlockEntityType.Builder.of(DisassemblerBlockEntity::new, ModBlocks.DISASSEMBLER.get()).build(null));

    private ModBlockEntities() {}
}
