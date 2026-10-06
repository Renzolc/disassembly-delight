package dev.renzo.disassemblydelight.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.renzo.disassemblydelight.DisassemblyDelight;
import dev.renzo.disassemblydelight.ModBlocks;
import dev.renzo.disassemblydelight.blockentity.DisassemblerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Rule C end to end in a real world: the Disassembler block empties containers into its outputs, or leaves them
 * whole. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(DisassemblyDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public class DisassemblerContainerGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);
    private static final int WAIT = 2 * DisassemblerBlockEntity.PROCESS_INTERVAL + 5;

    private static ItemStackHandler placeDisassembler(GameTestHelper helper, ItemStack input) {
        helper.setBlock(POS, ModBlocks.DISASSEMBLER.get());
        DisassemblerBlockEntity be = helper.getBlockEntity(POS);
        be.getItems().setStackInSlot(DisassemblerBlockEntity.INPUT_SLOT, input);
        return be.getItems();
    }

    private static List<ItemStack> outputs(ItemStackHandler items) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 1; i < DisassemblerBlockEntity.TOTAL_SLOTS; i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                out.add(items.getStackInSlot(i));
            }
        }
        return out;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static ItemStack shulker(List<ItemStack> contents) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return box;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shulkerBoxIsEmptiedIntoTheOutputs(GameTestHelper helper) {
        ItemStackHandler items = placeDisassembler(helper, shulker(List.of(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.STONE, 64))));
        helper.succeedWhen(() -> {
            List<ItemStack> out = outputs(items);
            helper.assertTrue(items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty(), "input not consumed yet");
            helper.assertTrue(count(out, Items.DIAMOND) == 5, "expected the 5 diamonds back, got " + out);
            helper.assertTrue(count(out, Items.STONE) == 64, "expected the 64 stone back, got " + out);
            helper.assertTrue(count(out, Items.SHULKER_SHELL) == 2 && count(out, Items.CHEST) == 1, "expected the box's own disassemble, got " + out);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullShulkerBoxPassesThroughWhole(GameTestHelper helper) {
        List<ItemStack> inside = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            inside.add(new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.IRON_INGOT, 64));
        }
        ItemStack box = shulker(inside);
        ItemStack expected = box.copy();
        ItemStackHandler items = placeDisassembler(helper, box);
        helper.succeedWhen(() -> {
            List<ItemStack> out = outputs(items);
            helper.assertTrue(out.size() == 1 && ItemStack.isSameItemSameComponents(out.get(0), expected),
                    "a box whose contents cannot fit 9 slots must come out whole, got " + out);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void blockedOutputsKeepTheContainerWhole(GameTestHelper helper) {
        ItemStack box = shulker(List.of(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.EMERALD, 3)));
        ItemStack expected = box.copy();
        ItemStackHandler items = placeDisassembler(helper, box);
        for (int i = 1; i < DisassemblerBlockEntity.TOTAL_SLOTS - 1; i++) {
            items.setStackInSlot(i, new ItemStack(Items.DIRT, 64));
        }
        // One free slot: room for the box itself, not for diamonds + emeralds + shells + chest.
        helper.runAfterDelay(WAIT * 2, () -> {
            ItemStack input = items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT);
            helper.assertTrue(ItemStack.isSameItemSameComponents(input, expected) && input.getCount() == 1,
                    "the box must wait in the input with its contents, got " + input);
            List<ItemStack> out = outputs(items);
            helper.assertTrue(count(out, Items.DIRT) == 8 * 64 && out.size() == 8, "outputs must be untouched, got " + out);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chestWithBlockEntityItemsPassesThrough(GameTestHelper helper) {
        ItemStack chest = new ItemStack(Items.CHEST);
        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:chest");
        ListTag list = new ListTag();
        CompoundTag slot = (CompoundTag) new ItemStack(Items.DIAMOND, 9).save(helper.getLevel().registryAccess());
        slot.putByte("Slot", (byte) 0);
        list.add(slot);
        be.put("Items", list);
        chest.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(be));
        ItemStack expected = chest.copy();
        ItemStackHandler items = placeDisassembler(helper, chest);
        helper.succeedWhen(() -> {
            List<ItemStack> out = outputs(items);
            helper.assertTrue(out.size() == 1 && ItemStack.isSameItemSameComponents(out.get(0), expected),
                    "a chest with unreadable block entity items must pass through unchanged, got " + out);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void emptyChestStillDisassembles(GameTestHelper helper) {
        ItemStackHandler items = placeDisassembler(helper, new ItemStack(Items.CHEST));
        helper.succeedWhen(() -> {
            List<ItemStack> out = outputs(items);
            helper.assertTrue(items.getStackInSlot(DisassemblerBlockEntity.INPUT_SLOT).isEmpty() && !out.isEmpty()
                    && count(out, Items.CHEST) == 0, "an empty chest disassembles as before, got " + out);
        });
    }
}
