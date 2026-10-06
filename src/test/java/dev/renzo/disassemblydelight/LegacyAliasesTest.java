package dev.renzo.disassemblydelight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.renzo.disassemblydelight.blockentity.DisassemblerBlockEntity;
import dev.renzo.disassemblydelight.recipe.FullUncraftRecipe;
import dev.renzo.disassemblydelight.recipe.ModRecipeTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Migration from Farmer's Delight Tweaks 1.0.x: every old fd_storage_compat id resolves to the Disassembler,
 * and data saved under the old ids (block entity inventory, items, backpack upgrades, datapack recipes) loads.
 */
class LegacyAliasesTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void setUp() {
        registries = VanillaRegistries.createLookup();
    }

    private static ResourceLocation old(String path) {
        return ResourceLocation.fromNamespaceAndPath("fd_storage_compat", path);
    }

    @Test
    void oldBlockIdResolvesToTheDisassembler() {
        assertSame(ModBlocks.DISASSEMBLER.get(), BuiltInRegistries.BLOCK.get(old("decrafter")));
        assertTrue(BuiltInRegistries.BLOCK.getOptional(old("decrafter")).isPresent());
        assertEquals(DisassemblyDelight.id("disassembler"), BuiltInRegistries.BLOCK.getKey(BuiltInRegistries.BLOCK.get(old("decrafter"))));
    }

    @Test
    void oldItemIdResolvesToTheDisassembler() {
        Item item = BuiltInRegistries.ITEM.get(old("decrafter"));
        assertNotSame(Items.AIR, item);
        assertSame(ModBlocks.DISASSEMBLER.get().asItem(), item);
    }

    @Test
    void oldBlockEntityAndMenuIdsResolve() {
        assertSame(ModBlockEntities.DISASSEMBLER.get(), BuiltInRegistries.BLOCK_ENTITY_TYPE.get(old("decrafter")));
        assertSame(ModMenus.DISASSEMBLER.get(), BuiltInRegistries.MENU.get(old("decrafter")));
    }

    @Test
    void oldRecipeTypeAndSerializerResolve() {
        assertSame(ModRecipeTypes.FULL_UNCRAFT.get(), BuiltInRegistries.RECIPE_TYPE.get(old("full_uncraft")));
        assertSame(ModRecipeTypes.FULL_UNCRAFT_SERIALIZER.get(), BuiltInRegistries.RECIPE_SERIALIZER.get(old("full_uncraft")));
    }

    @Test
    void oldUpgradeIdResolvesWhenSophisticatedBackpacksIsLoaded() {
        assertTrue(ModList.get().isLoaded("sophisticatedbackpacks"), "the test game loads Sophisticated Backpacks");
        Item upgrade = BuiltInRegistries.ITEM.get(DisassemblyDelight.id("disassembler_upgrade"));
        assertNotSame(Items.AIR, upgrade);
        assertSame(upgrade, BuiltInRegistries.ITEM.get(old("decrafter_upgrade")));
    }

    @Test
    void savedItemStackWithOldIdLoads() throws Exception {
        ItemStack stack = ItemStack.parse(registries, TagParser.parseTag("{id:\"fd_storage_compat:decrafter\",count:3}")).orElseThrow();
        assertSame(ModBlocks.DISASSEMBLER.get().asItem(), stack.getItem());
        assertEquals(3, stack.getCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void upgradeSavedInABackpackKeepsItsInputSlot() throws Exception {
        // A backpack saves its upgrades as item stacks; the upgrade's input slot is the
        // sophisticatedcore:lenient_container component on that stack.
        Item upgrade = BuiltInRegistries.ITEM.get(DisassemblyDelight.id("disassembler_upgrade"));
        DataComponentType<ItemContainerContents> lenient = (DataComponentType<ItemContainerContents>)
                BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("sophisticatedcore:lenient_container"));
        assertNotNull(lenient, "Sophisticated Core component registered");
        ItemStack saved = new ItemStack(upgrade);
        saved.set(lenient, ItemContainerContents.fromItems(List.of(new ItemStack(Items.OAK_PLANKS, 7))));
        CompoundTag tag = (CompoundTag) saved.save(registries);
        tag.putString("id", "fd_storage_compat:decrafter_upgrade"); // what 1.0.x wrote

        ItemStack loaded = ItemStack.parse(registries, tag).orElseThrow();
        assertSame(upgrade, loaded.getItem());
        ItemContainerContents contents = loaded.get(lenient);
        assertNotNull(contents, "input slot component survives");
        ItemStack input = contents.copyOne();
        assertSame(Items.OAK_PLANKS, input.getItem());
        assertEquals(7, input.getCount());
    }

    @Test
    void decrafterBlockEntitySavedBy1_0_xLoadsWithItsInventory() throws Exception {
        // Exactly what FDT 1.0.x DecrafterBlockEntity.saveAdditional wrote (keys "Items" and "Progress").
        CompoundTag tag = TagParser.parseTag("{id:\"fd_storage_compat:decrafter\",x:0,y:64,z:0,Progress:7,"
                + "Items:{Size:10,Items:[{Slot:0,id:\"minecraft:oak_planks\",count:5},{Slot:3,id:\"minecraft:stick\",count:2}]}}");
        BlockEntity loaded = BlockEntity.loadStatic(new BlockPos(0, 64, 0), ModBlocks.DISASSEMBLER.get().defaultBlockState(), tag, registries);
        DisassemblerBlockEntity be = assertInstanceOf(DisassemblerBlockEntity.class, loaded);
        assertSame(ModBlockEntities.DISASSEMBLER.get(), be.getType());
        ItemStack input = be.getItems().getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT);
        assertSame(Items.OAK_PLANKS, input.getItem());
        assertEquals(5, input.getCount());
        assertSame(Items.STICK, be.getItems().getStackInSlot(3).getItem());
        assertEquals(2, be.getItems().getStackInSlot(3).getCount());

        // Saving again writes the same keys under the new id.
        CompoundTag resaved = be.saveWithFullMetadata(registries);
        assertEquals("disassembly_delight:disassembler", resaved.getString("id"));
        assertTrue(resaved.contains("Items") && resaved.contains("Progress"));
        assertEquals(7, resaved.getInt("Progress"));
    }

    @Test
    void thirdPartyRecipeWithOldTypeStillParses() {
        String json = "{\"type\":\"fd_storage_compat:full_uncraft\",\"input\":{\"item\":\"minecraft:dead_bush\"},"
                + "\"consume\":1,\"results\":[{\"id\":\"minecraft:stick\",\"count\":2}]}";
        Recipe<?> recipe = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow();
        FullUncraftRecipe full = assertInstanceOf(FullUncraftRecipe.class, recipe);
        assertSame(ModRecipeTypes.FULL_UNCRAFT.get(), full.getType());
        assertTrue(full.input().test(new ItemStack(Items.DEAD_BUSH)));
        assertEquals(2, full.results().getFirst().getCount());
    }
}
