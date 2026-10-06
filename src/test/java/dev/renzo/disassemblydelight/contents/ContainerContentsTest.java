package dev.renzo.disassemblydelight.contents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Rule C: a disassembled container gives back everything it holds, or it is left alone. Runs in a bootstrapped
 * game (FML JUnit), so real items, components and codecs are used.
 */
class ContainerContentsTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void setUp() {
        registries = VanillaRegistries.createLookup();
    }

    private static Optional<ContainerContents.Extraction> extract(ItemStack stack) {
        return ContainerContents.extract(null, registries, stack);
    }

    private static int total(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @Test
    void rulesFileLoadsAndIsConsistent() {
        ContainerRules rules = ContainerRules.get();
        assertTrue(rules.problems().isEmpty(), () -> "container_rules.json problems: " + rules.problems());
        assertFalse(rules.readers.isEmpty());
        assertFalse(rules.items.isEmpty());
    }

    @Test
    void plainItemHasNoContents() {
        Optional<ContainerContents.Extraction> result = extract(new ItemStack(Items.OAK_PLANKS, 12));
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    void shulkerBoxContentsAreReturned() {
        ItemStack box = new ItemStack(Items.RED_SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                new ItemStack(Items.DIAMOND, 5), ItemStack.EMPTY, new ItemStack(Items.STONE, 64))));
        List<ItemStack> stacks = extract(box).orElseThrow().stacks();
        assertEquals(5, total(stacks, Items.DIAMOND));
        assertEquals(64, total(stacks, Items.STONE));
    }

    @Test
    void nestedShulkerIsReturnedWhole() {
        ItemStack inner = new ItemStack(Items.BLUE_SHULKER_BOX);
        inner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.EMERALD, 3))));
        ItemStack outer = new ItemStack(Items.SHULKER_BOX);
        outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inner)));
        List<ItemStack> stacks = extract(outer).orElseThrow().stacks();
        assertEquals(1, stacks.size());
        assertTrue(ItemStack.isSameItemSameComponents(inner, stacks.get(0)), "inner box keeps its own contents");
    }

    @Test
    void bundleContentsAreReturned() {
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.ARROW, 16), new ItemStack(Items.APPLE, 2))));
        List<ItemStack> stacks = extract(bundle).orElseThrow().stacks();
        assertEquals(16, total(stacks, Items.ARROW));
        assertEquals(2, total(stacks, Items.APPLE));
    }

    @Test
    void chargedCrossbowReturnsItsArrow() {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.SPECTRAL_ARROW)));
        assertEquals(1, total(extract(crossbow).orElseThrow().stacks(), Items.SPECTRAL_ARROW));
    }

    @Test
    void decoratedPotReturnsItsItemButNotSherds() {
        ItemStack pot = new ItemStack(Items.DECORATED_POT);
        pot.set(DataComponents.POT_DECORATIONS, new net.minecraft.world.level.block.entity.PotDecorations(
                Items.ANGLER_POTTERY_SHERD, Items.BRICK, Items.BRICK, Items.ARMS_UP_POTTERY_SHERD));
        pot.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.GOLD_INGOT, 7))));
        List<ItemStack> stacks = extract(pot).orElseThrow().stacks();
        assertEquals(1, stacks.size());
        assertEquals(7, total(stacks, Items.GOLD_INGOT));
    }

    @Test
    void chestWithBlockEntityItemsPassesThrough() {
        ItemStack chest = new ItemStack(Items.CHEST);
        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:chest");
        ListTag list = new ListTag();
        CompoundTag slot = (CompoundTag) new ItemStack(Items.DIAMOND, 9).save(registries);
        slot.putByte("Slot", (byte) 0);
        list.add(slot);
        be.put("Items", list);
        chest.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(be));
        assertTrue(extract(chest).isEmpty(), "block entity inventory is not readable here, so the chest must pass through");
    }

    @Test
    void blockEntityDataWithOnlyIdIsHarmless() {
        ItemStack chest = new ItemStack(Items.CHEST);
        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:chest");
        chest.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(be));
        assertTrue(extract(chest).orElseThrow().isEmpty());
    }

    @Test
    void lootTableContainerPassesThrough() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(
                ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                        ResourceLocation.withDefaultNamespace("chests/simple_dungeon")), 0L));
        assertTrue(extract(box).isEmpty());
    }

    @Test
    void beehiveWithBeesPassesThrough() {
        ItemStack hive = new ItemStack(Items.BEEHIVE);
        CompoundTag bee = new CompoundTag();
        bee.putString("id", "minecraft:bee");
        hive.set(DataComponents.BEES, List.of(new net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant(
                CustomData.of(bee), 0, 0)));
        assertTrue(extract(hive).isEmpty());
    }

    @Test
    void customDataHoldingAnItemPassesThrough() {
        ItemStack stack = new ItemStack(Items.STICK);
        CompoundTag tag = new CompoundTag();
        CompoundTag inv = new CompoundTag();
        inv.put("Stored", new ItemStack(Items.NETHERITE_INGOT, 2).save(registries));
        tag.put("SomeModInventory", inv);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertTrue(extract(stack).isEmpty(), "unknown custom data with an item stack must not be destroyed");
    }

    @Test
    void plainCustomDataIsFine() {
        ItemStack stack = new ItemStack(Items.STICK);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "not_an_item_just_a_label");
        tag.putInt("Uses", 4);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertTrue(extract(stack).orElseThrow().isEmpty());
    }

    // --- mod component shapes, read from NBT exactly as each mod's codec writes it ---

    private static CompoundTag stackTag(String id, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", count);
        return tag;
    }

    private static net.minecraft.nbt.Tag parse(String snbt) {
        try {
            return net.minecraft.nbt.TagParser.parseTag("{v:" + snbt + "}").get("v");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void supplementariesQuiverAndLunchBasketShape() {
        // QuiverContent / LunchBasketContent codec: {items:[ItemStack.OPTIONAL_CODEC...], selected_slot:int}
        List<ItemStack> read = ContainerContents.readEncoded("stack_list", "items",
                parse("{items:[{id:\"minecraft:arrow\",count:12},{},{id:\"minecraft:spectral_arrow\",count:3}],selected_slot:0}"), registries);
        assertEquals(12, total(read, Items.ARROW));
        assertEquals(3, total(read, Items.SPECTRAL_ARROW));
    }

    @Test
    void createPackageAndVanillaContainerShape() {
        // ItemContainerContents codec (minecraft:container, create:package_contents): [{slot, item}]
        List<ItemStack> read = ContainerContents.readEncoded("item_container", null,
                parse("[{slot:0,item:{id:\"minecraft:diamond\",count:2}},{slot:5,item:{id:\"minecraft:cobblestone\",count:64}}]"), registries);
        assertEquals(2, total(read, Items.DIAMOND));
        assertEquals(64, total(read, Items.COBBLESTONE));
    }

    @Test
    void tideRodPartsAndBaitShape() {
        // tide:fishing_hook/line/bobber: one encoded ItemStack, {} when empty; tide:bait_contents: a list of stacks
        assertEquals(1, total(ContainerContents.readEncoded("single_stack", null, stackTag("minecraft:string", 1), registries), Items.STRING));
        assertTrue(ContainerContents.readEncoded("single_stack", null, new CompoundTag(), registries).isEmpty());
        List<ItemStack> bait = ContainerContents.readEncoded("stack_list", null,
                parse("[{id:\"minecraft:wheat_seeds\",count:30}]"), registries);
        assertEquals(30, total(bait, Items.WHEAT_SEEDS));
    }

    @Test
    void constructionWandCoresShape() {
        // constructionwand:cores: {cores:["ns:item", ...]}; each id is one installed core item
        List<ItemStack> cores = ContainerContents.readEncoded("item_id_list", "cores", parse("{cores:[\"minecraft:diamond\"]}"), registries);
        assertEquals(1, total(cores, Items.DIAMOND));
        assertTrue(ContainerContents.readEncoded("item_id_list", "cores", new CompoundTag(), registries).isEmpty());
        assertEquals(null, ContainerContents.readEncoded("item_id_list", "cores", parse("{cores:[\"nosuchmod:core\"]}"), registries),
                "an unknown core id must make the wand pass through, not vanish");
    }

    @Test
    void malformedOrUnknownDataIsUnreadable() {
        assertEquals(null, ContainerContents.readEncoded("stack_list", "items", parse("{items:[\"minecraft:arrow\"]}"), registries));
        assertEquals(null, ContainerContents.readEncoded("stack_list", null, parse("[{id:\"nosuchmod:thing\",count:1}]"), registries),
                "an item from a missing mod cannot be returned, so the container must pass through");
        assertEquals(null, ContainerContents.readEncoded("item_container", null, parse("{slot:0}"), registries));
        assertEquals(null, ContainerContents.readEncoded("single_stack", null, null, registries));
        assertEquals(null, ContainerContents.readEncoded("no_such_kind", null, new CompoundTag(), registries));
    }

    @Test
    void farmersDelightMealComponentIsRead() {
        DataComponentType<?> meal = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("farmersdelight:meal"));
        DataComponentType<?> serving = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("farmersdelight:container"));
        org.junit.jupiter.api.Assumptions.assumeTrue(meal != null && serving != null, "Farmer's Delight not loaded");
        ItemStack pot = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:cooking_pot")));
        setFromNbt(pot, meal, new ItemStack(Items.MUSHROOM_STEW, 3).save(registries));
        setFromNbt(pot, serving, new ItemStack(Items.BOWL).save(registries));
        List<ItemStack> stacks = extract(pot).orElseThrow().stacks();
        assertEquals(3, total(stacks, Items.MUSHROOM_STEW));
        assertEquals(0, total(stacks, Items.BOWL), "the serving container is a hint, not a stored item");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setFromNbt(ItemStack stack, DataComponentType type, net.minecraft.nbt.Tag tag) {
        Object value = type.codec().parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        stack.set(type, value);
    }

    @Test
    void forcedPassThroughIdsAreListed() {
        ContainerRules rules = ContainerRules.get();
        assertTrue(rules.passThroughItems.contains("create:minecart_contraption"));
        assertTrue(rules.passThroughItems.contains("someassemblyrequired:sandwich"));
    }

    // --- ContainerDisassembly: contents + results, all or nothing ---

    @Test
    void planPutsContentsFirstThenResults() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 5))));
        ContainerDisassembly.Base base = new ContainerDisassembly.Base(1, List.of(new ItemStack(Items.SHULKER_SHELL, 2), new ItemStack(Items.CHEST)));
        ContainerDisassembly.Plan plan = ContainerDisassembly.plan(null, registries, box, base).orElseThrow();
        assertEquals(1, plan.consume());
        assertTrue(plan.outputs().get(0).is(Items.DIAMOND));
        assertEquals(5, total(plan.outputs(), Items.DIAMOND));
        assertEquals(2, total(plan.outputs(), Items.SHULKER_SHELL));
        assertEquals(1, total(plan.outputs(), Items.CHEST));
    }

    @Test
    void planMultipliesContentsByConsumedItems() {
        ItemStack bundles = new ItemStack(Items.BUNDLE, 1);
        bundles.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.ARROW, 10))));
        bundles.setCount(2); // bypass max stack size on purpose: both bundles carry the same contents
        ContainerDisassembly.Base base = new ContainerDisassembly.Base(2, List.of(new ItemStack(Items.LEATHER, 3)));
        ContainerDisassembly.Plan plan = ContainerDisassembly.plan(null, registries, bundles, base).orElseThrow();
        assertEquals(20, total(plan.outputs(), Items.ARROW));
    }

    @Test
    void unreadableContainerHasNoPlan() {
        ItemStack chest = new ItemStack(Items.CHEST);
        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:chest");
        be.put("Items", new ListTag());
        be.putString("Lock", "key");
        chest.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(be));
        assertTrue(ContainerDisassembly.plan(null, registries, chest, new ContainerDisassembly.Base(1, List.of(new ItemStack(Items.OAK_PLANKS, 8)))).isEmpty());
    }

    @Test
    void noRecipeMeansNoPlanForNormalContainers() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        assertTrue(ContainerDisassembly.plan(null, registries, box, null).isEmpty());
    }

    @Test
    void oversizedStacksAreSplit() {
        List<ItemStack> split = ContainerDisassembly.splitToStackSize(List.of(new ItemStack(Items.STONE, 200), new ItemStack(Items.ENDER_PEARL, 20)));
        assertEquals(List.of(64, 64, 64, 8, 16, 4), split.stream().map(ItemStack::getCount).toList());
    }

    @Test
    void insertIsAllOrNothing() {
        ItemStackHandler outputs = new ItemStackHandler(3);
        outputs.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        outputs.setStackInSlot(1, new ItemStack(Items.STONE, 60));
        List<ItemStack> tooMuch = List.of(new ItemStack(Items.STONE, 4), new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.EMERALD, 1));
        assertFalse(ContainerDisassembly.insertAllOrNothing(outputs, tooMuch));
        assertEquals(64, outputs.getStackInSlot(0).getCount());
        assertEquals(60, outputs.getStackInSlot(1).getCount());
        assertTrue(outputs.getStackInSlot(2).isEmpty(), "a failed insert leaves the outputs exactly as they were");

        List<ItemStack> fits = List.of(new ItemStack(Items.STONE, 4), new ItemStack(Items.DIAMOND, 64));
        assertTrue(ContainerDisassembly.insertAllOrNothing(outputs, fits));
        assertEquals(64, outputs.getStackInSlot(1).getCount());
        assertEquals(64, outputs.getStackInSlot(2).getCount());
    }

    @Test
    void nothingIsLostForAFullShulker() {
        List<ItemStack> inside = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            inside.add(new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.IRON_INGOT, 64));
        }
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        ContainerDisassembly.Plan plan = ContainerDisassembly.plan(null, registries, box,
                new ContainerDisassembly.Base(1, List.of(new ItemStack(Items.SHULKER_SHELL, 2), new ItemStack(Items.CHEST)))).orElseThrow();
        assertEquals(14 * 64, total(plan.outputs(), Items.COBBLESTONE));
        assertEquals(13 * 64, total(plan.outputs(), Items.IRON_INGOT));
        // Nine output slots cannot hold 27 stacks: the insert fails and the outputs stay empty.
        ItemStackHandler nine = new ItemStackHandler(9);
        assertFalse(ContainerDisassembly.insertAllOrNothing(nine, plan.outputs()));
        for (int i = 0; i < 9; i++) {
            assertTrue(nine.getStackInSlot(i).isEmpty());
        }
    }

    // --- pure NBT helpers ---

    @Test
    void nbtScanFindsNestedStacks() {
        CompoundTag root = new CompoundTag();
        CompoundTag deep = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:diamond");
        item.putInt("count", 3);
        list.add(item);
        deep.put("list", list);
        root.put("a", deep);
        assertTrue(ItemNbtScan.containsItemStack(root, ContainerContents.itemIdPredicate()));

        CompoundTag notItem = new CompoundTag();
        notItem.putString("id", "minecraft:zombie");
        notItem.putInt("count", 1);
        assertFalse(ItemNbtScan.containsItemStack(notItem, ContainerContents.itemIdPredicate()));
    }

    @Test
    void nbtPathHelpers() {
        CompoundTag root = new CompoundTag();
        CompoundTag sack = new CompoundTag();
        sack.put("Items", new ListTag());
        sack.putInt("Size", 9);
        root.put("VoidSack", sack);
        assertTrue(ItemNbtScan.at(root, "VoidSack/Items") instanceof ListTag);
        CompoundTag rest = ItemNbtScan.without(root, "VoidSack/Items");
        assertFalse(rest.getCompound("VoidSack").contains("Items"));
        assertTrue(root.getCompound("VoidSack").contains("Items"), "without() must not change the original");
    }
}
