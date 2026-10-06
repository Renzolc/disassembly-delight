package dev.renzo.disassemblydelight;

import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DisassemblyDelight.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.disassembly_delight"))
                    .icon(() -> new ItemStack(ModBlocks.DISASSEMBLER.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.DISASSEMBLER.get());
                        acceptIfPresent(output, "disassembler_upgrade");
                    })
                    .build());

    private static void acceptIfPresent(CreativeModeTab.Output output, String path) {
        Item item = BuiltInRegistries.ITEM.get(DisassemblyDelight.id(path));
        if (item != Items.AIR) {
            output.accept(item);
        }
    }

    private ModCreativeTabs() {}
}
