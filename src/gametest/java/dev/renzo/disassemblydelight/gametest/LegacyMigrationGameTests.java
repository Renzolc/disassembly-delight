package dev.renzo.disassemblydelight.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.renzo.disassemblydelight.DisassemblyDelight;
import dev.renzo.disassemblydelight.ModBlockEntities;
import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.blockentity.DisassemblerBlockEntity;
import dev.renzo.disassemblydelight.recipe.FullUncraftRecipe;
import dev.renzo.disassemblydelight.recipe.ModRecipeTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Worlds from Farmer's Delight Tweaks 1.0.x: a Decrafter saved as fd_storage_compat:decrafter comes back as a
 * working Disassembler with its inventory. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(DisassemblyDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public class LegacyMigrationGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    private static ResourceLocation old(String path) {
        return ResourceLocation.fromNamespaceAndPath("fd_storage_compat", path);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void legacyDecrafterLoadsAndKeepsWorking(GameTestHelper helper) {
        // The block a 1.0.x chunk palette names is the Disassembler.
        Block legacy = BuiltInRegistries.BLOCK.get(old("decrafter"));
        helper.assertTrue(legacy == ModBlocks.DISASSEMBLER.get(), "fd_storage_compat:decrafter must resolve to the Disassembler, got " + legacy);
        helper.setBlock(POS, legacy);
        DisassemblerBlockEntity be = helper.getBlockEntity(POS);
        helper.assertTrue(be.getType() == ModBlockEntities.DISASSEMBLER.get(), "wrong block entity type " + be.getType());

        // Load the block entity data a 1.0.x Decrafter saved: 4 oak planks waiting in the input.
        CompoundTag saved;
        try {
            saved = TagParser.parseTag("{id:\"fd_storage_compat:decrafter\",Progress:0,"
                    + "Items:{Size:10,Items:[{Slot:0,id:\"minecraft:oak_planks\",count:4}]}}");
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
        be.loadWithComponents(saved, helper.getLevel().registryAccess());
        ItemStackHandler items = be.getItems();
        helper.assertTrue(items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).is(Items.OAK_PLANKS)
                && items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).getCount() == 4, "saved input did not load");

        // And it keeps disassembling: planks -> 2 slabs each.
        helper.succeedWhen(() -> {
            int slabs = 0;
            for (int i = 1; i < DisassemblerBlockEntity.TOTAL_SLOTS; i++) {
                if (items.getStackInSlot(i).is(Items.OAK_SLAB)) {
                    slabs += items.getStackInSlot(i).getCount();
                }
            }
            helper.assertTrue(items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty() && slabs == 8,
                    "expected 8 oak slabs from the loaded planks, got " + slabs);
        });
    }

    @GameTest(template = "empty")
    public static void legacyItemsResolve(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.ITEM.get(old("decrafter")) == ModBlocks.DISASSEMBLER.get().asItem(),
                "fd_storage_compat:decrafter item must be the Disassembler");
        helper.assertTrue(BuiltInRegistries.MENU.get(old("decrafter")) != null
                && BuiltInRegistries.MENU.get(old("decrafter")) == BuiltInRegistries.MENU.get(DisassemblyDelight.id("disassembler")),
                "menu alias");
        var upgrade = BuiltInRegistries.ITEM.get(DisassemblyDelight.id("disassembler_upgrade"));
        helper.assertTrue(upgrade != Items.AIR, "Sophisticated Backpacks is loaded in the test game, so the upgrade exists");
        helper.assertTrue(BuiltInRegistries.ITEM.get(old("decrafter_upgrade")) == upgrade,
                "fd_storage_compat:decrafter_upgrade must be the Disassembler Upgrade");
        ItemStack fromSave = ItemStack.parse(helper.getLevel().registryAccess(), parse("{id:\"fd_storage_compat:decrafter_upgrade\",count:1}")).orElseThrow();
        helper.assertTrue(fromSave.is(upgrade), "saved upgrade stack loads as the Disassembler Upgrade, got " + fromSave);
        helper.succeed();
    }

    /** src/gametest/resources/.../legacy_type_full_uncraft.json still says "type": "fd_storage_compat:full_uncraft". */
    @GameTest(template = "empty")
    public static void datapackRecipeWithLegacyTypeLoads(GameTestHelper helper) {
        List<String> ids = new ArrayList<>();
        FullUncraftRecipe found = null;
        for (RecipeHolder<FullUncraftRecipe> holder : helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipeTypes.FULL_UNCRAFT.get())) {
            if (holder.id().equals(DisassemblyDelight.id("legacy_type_full_uncraft"))) {
                found = holder.value();
            }
            ids.add(holder.id().toString());
        }
        helper.assertTrue(found != null, "legacy-typed recipe missing from the recipe manager (" + ids.size() + " full_uncraft recipes)");
        helper.assertTrue(found.input().test(new ItemStack(Items.DEAD_BUSH)) && found.results().getFirst().is(Items.STICK),
                "legacy-typed recipe parsed wrong");
        helper.assertTrue(FullUncraftRecipe.find(helper.getLevel(), new ItemStack(Items.DEAD_BUSH)).isPresent(), "lookup finds it");
        helper.succeed();
    }

    private static CompoundTag parse(String snbt) {
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
