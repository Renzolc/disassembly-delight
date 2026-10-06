package dev.renzo.disassemblydelight;

import java.util.function.Supplier;

import dev.renzo.disassemblydelight.menu.DisassemblerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, DisassemblyDelight.MOD_ID);

    public static final Supplier<MenuType<DisassemblerMenu>> DISASSEMBLER =
            MENUS.register("disassembler", () -> IMenuTypeExtension.create(DisassemblerMenu::new));

    private ModMenus() {}
}
