package dev.renzo.disassemblydelight;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.renzo.disassemblydelight.blockentity.DisassemblerBlockEntity;
import dev.renzo.disassemblydelight.client.ClientModEvents;
import dev.renzo.disassemblydelight.recipe.ModRecipeTypes;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(DisassemblyDelight.MOD_ID)
public class DisassemblyDelight {
    public static final String MOD_ID = "disassembly_delight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DisassemblyDelight(IEventBus modEventBus) {
        // Old Farmer's Delight Tweaks ids (fd_storage_compat:decrafter...) keep loading as the Disassembler.
        LegacyAliases.register();
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModRecipeTypes.SERIALIZERS.register(modEventBus);
        modEventBus.addListener(this::registerCapabilities);
        if (ModList.get().isLoaded("sophisticatedbackpacks")) {
            dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeSetup.init(modEventBus);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                dev.renzo.disassemblydelight.client.DisassemblerUpgradeClientSetup.init(modEventBus);
            }
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientModEvents.register(modEventBus);
        }
        LOGGER.info("Disassembly Delight loaded");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DISASSEMBLER.get(),
                (DisassemblerBlockEntity be, Direction side) -> be.getHandlerForSide(side));
    }
}
