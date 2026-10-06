package dev.renzo.disassemblydelight;

import dev.renzo.disassemblydelight.recipe.ModRecipeTypes;

import net.minecraft.resources.ResourceLocation;

/**
 * Migration from Farmer's Delight Tweaks 1.0.x, where the Disassembler was the "Decrafter" in the
 * {@code fd_storage_compat} namespace. NeoForge registry aliases work across namespaces: a lookup of an old id
 * that is no longer registered resolves to the new entry. That covers blocks in saved chunks, block entities
 * (their saved {@code id}), items in inventories and backpacks, menus, and third-party datapack recipes that
 * still say {@code "type": "fd_storage_compat:full_uncraft"}.
 *
 * <p>An alias only applies while the old id is free, so Farmer's Delight Tweaks must be 1.1.0 or newer
 * (enforced in neoforge.mods.toml).
 */
public final class LegacyAliases {
    public static final String LEGACY_NAMESPACE = "fd_storage_compat";

    public static final ResourceLocation OLD_DECRAFTER = legacy("decrafter");
    public static final ResourceLocation OLD_DECRAFTER_UPGRADE = legacy("decrafter_upgrade");
    public static final ResourceLocation OLD_FULL_UNCRAFT = legacy("full_uncraft");

    public static final ResourceLocation DISASSEMBLER = DisassemblyDelight.id("disassembler");
    public static final ResourceLocation DISASSEMBLER_UPGRADE = DisassemblyDelight.id("disassembler_upgrade");
    public static final ResourceLocation FULL_UNCRAFT = DisassemblyDelight.id("full_uncraft");

    private LegacyAliases() {
    }

    public static ResourceLocation legacy(String path) {
        return ResourceLocation.fromNamespaceAndPath(LEGACY_NAMESPACE, path);
    }

    /** Must run before the registers fire (from the mod constructor). */
    static void register() {
        ModBlocks.BLOCKS.addAlias(OLD_DECRAFTER, DISASSEMBLER);
        ModItems.ITEMS.addAlias(OLD_DECRAFTER, DISASSEMBLER);
        // Resolves to the upgrade when Sophisticated Backpacks is installed; to nothing (air) otherwise, which is
        // what an unknown item already was.
        ModItems.ITEMS.addAlias(OLD_DECRAFTER_UPGRADE, DISASSEMBLER_UPGRADE);
        ModBlockEntities.BLOCK_ENTITIES.addAlias(OLD_DECRAFTER, DISASSEMBLER);
        ModMenus.MENUS.addAlias(OLD_DECRAFTER, DISASSEMBLER);
        ModRecipeTypes.RECIPE_TYPES.addAlias(OLD_FULL_UNCRAFT, FULL_UNCRAFT);
        ModRecipeTypes.SERIALIZERS.addAlias(OLD_FULL_UNCRAFT, FULL_UNCRAFT);
    }
}
