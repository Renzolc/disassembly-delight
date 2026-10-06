package dev.renzo.disassemblydelight.contents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.renzo.disassemblydelight.DisassemblyDelight;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Reads the items stored inside an item (Rule C) so a disassemble can hand them back before the container is used up.
 *
 * <p>{@link Optional#empty()} means "this item holds something that cannot be read safely": the caller must
 * pass the item through unchanged. Nothing here hard-depends on another mod; mod components are looked up by id
 * from {@link ContainerRules}.
 *
 * <p>Order: forced pass-through ids, Sophisticated storage (contents live in saved data, not on the stack),
 * then every component in the stack's patch: a known reader, a ghost (filters, previews), a pass-through
 * component (fluids, mobs, contraptions, loot tables), block entity data, custom data, and finally the generic
 * guard: any other persistent component whose encoded data contains an item stack makes the item pass through.
 * Last, an item handler capability must agree with what was read.
 */
public final class ContainerContents {
    private static final ResourceLocation BLOCK_ENTITY_DATA = ResourceLocation.withDefaultNamespace("block_entity_data");
    private static final ResourceLocation CUSTOM_DATA = ResourceLocation.withDefaultNamespace("custom_data");
    private static final ResourceLocation STORAGE_UUID = ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "storage_uuid");

    private ContainerContents() {
    }

    /**
     * Stored items plus a hook to run after they were inserted. {@code external} contents live outside the stack
     * (Sophisticated saved data): they belong to exactly one item and must be cleared after the insert.
     */
    public record Extraction(List<ItemStack> stacks, Runnable clearAfterInsert, boolean external) {
        public static final Runnable NOTHING = () -> {
        };

        public static Extraction none() {
            return new Extraction(List.of(), NOTHING, false);
        }

        public boolean isEmpty() {
            return stacks.isEmpty();
        }
    }

    public static Optional<Extraction> extract(Level level, ItemStack stack) {
        return extract(level, level.registryAccess(), stack);
    }

    public static Optional<Extraction> extract(@Nullable Level level, HolderLookup.Provider registries, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.of(Extraction.none());
        }
        try {
            return extractUnchecked(level, registries, stack);
        } catch (RuntimeException | LinkageError e) {
            warnOnce("extract:" + itemId(stack), "Disassembler left " + itemId(stack) + " alone; its contents could not be read", e);
            return Optional.empty();
        }
    }

    /** Items that always pass through: their contents are never read (contraptions, sandwiches, storage vehicles). */
    public static boolean isForcedPassThrough(ItemStack stack) {
        return ContainerRules.get().passThroughItems.contains(itemId(stack));
    }

    /** Items whose disassemble is "hand back the contents, the wrapper is used up" (Create packages). */
    public static boolean isContentsOnly(ItemStack stack) {
        for (String tag : ContainerRules.get().contentsOnlyTags) {
            ResourceLocation id = ResourceLocation.tryParse(tag);
            if (id != null && stack.is(TagKey.create(Registries.ITEM, id))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Extraction> extractUnchecked(@Nullable Level level, HolderLookup.Provider registries, ItemStack stack) {
        ContainerRules rules = ContainerRules.get();
        if (isForcedPassThrough(stack)) {
            return Optional.empty();
        }

        boolean sophisticated = SophisticatedContents.isStorageItem(stack);
        List<ItemStack> fromComponents = new ArrayList<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
            Optional<?> value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue; // a removed component holds nothing
            }
            DataComponentType<?> type = entry.getKey();
            ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            String id = key == null ? "" : key.toString();
            Object raw = value.get();

            String reader = rules.readers.get(id);
            if (reader != null) {
                List<ItemStack> read = read(reader, type, raw, registries);
                if (read == null) {
                    return Optional.empty();
                }
                fromComponents.addAll(read);
                continue;
            }
            if (rules.ghostComponents.contains(id)) {
                continue;
            }
            if (STORAGE_UUID.equals(key)) {
                if (!sophisticated) {
                    return Optional.empty(); // contents live in saved data that only the storage mod can read
                }
                continue;
            }
            if (rules.passThroughIfPresent.contains(id)) {
                Tag encoded = encode(type, raw, registries);
                if (encoded == null || !ItemNbtScan.isEmptyData(encoded)) {
                    return Optional.empty();
                }
                continue;
            }
            if (BLOCK_ENTITY_DATA.equals(key)) {
                if (!(raw instanceof CustomData data) || ItemNbtScan.blockEntityDataCarriesState(data.copyTag())) {
                    return Optional.empty();
                }
                continue;
            }
            if (CUSTOM_DATA.equals(key)) {
                if (!(raw instanceof CustomData data)) {
                    return Optional.empty();
                }
                List<ItemStack> read = readCustomData(stack, data.copyTag(), registries);
                if (read == null) {
                    return Optional.empty();
                }
                fromComponents.addAll(read);
                continue;
            }
            if (type.isTransient()) {
                continue; // never saved, so it cannot hold anything across a save
            }
            Tag encoded = encode(type, raw, registries);
            if (encoded == null || ItemNbtScan.containsItemStack(encoded, ContainerContents::isItemId)) {
                return Optional.empty();
            }
        }

        if (!sophisticated) {
            if (!capabilityAgrees(stack, fromComponents)) {
                return Optional.empty();
            }
            return Optional.of(new Extraction(List.copyOf(fromComponents), Extraction.NOTHING, false));
        }
        // Components are checked first so a linked endpoint or a fluid never reaches the storage wrapper.
        if (level == null) {
            return Optional.empty();
        }
        Optional<Extraction> stored = SophisticatedContents.extract(level, stack);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> stacks = new ArrayList<>(stored.get().stacks());
        stacks.addAll(fromComponents);
        return Optional.of(new Extraction(List.copyOf(stacks), stored.get().clearAfterInsert(), true));
    }

    /** Returns null when the component cannot be read. */
    @Nullable
    private static List<ItemStack> read(String reader, DataComponentType<?> type, Object raw, HolderLookup.Provider registries) {
        String kind = ContainerRules.kindOf(reader);
        String arg = ContainerRules.argOf(reader);
        List<ItemStack> out = new ArrayList<>();
        switch (kind) {
            case "item_container" -> {
                if (raw instanceof ItemContainerContents contents) {
                    contents.nonEmptyItemsCopy().forEach(out::add);
                    return out;
                }
                return readEncoded(kind, arg, encode(type, raw, registries), registries);
            }
            case "bundle" -> {
                if (!(raw instanceof BundleContents bundle)) {
                    return null;
                }
                bundle.itemCopyStream().forEach(item -> addIfPresent(out, item));
                return out;
            }
            case "charged_projectiles" -> {
                if (!(raw instanceof ChargedProjectiles projectiles)) {
                    return null;
                }
                for (ItemStack item : projectiles.getItems()) {
                    addIfPresent(out, item.copy());
                }
                return out;
            }
            case "item_handler" -> {
                if (!(raw instanceof IItemHandler handler)) {
                    return null;
                }
                copySlots(handler, out);
                return out;
            }
            case "single_stack" -> {
                if (raw instanceof ItemStack item) {
                    addIfPresent(out, item.copy());
                    return out;
                }
                return readEncoded(kind, arg, raw instanceof Tag tag ? tag : encode(type, raw, registries), registries);
            }
            case "stack_list", "item_id_list" -> {
                return readEncoded(kind, arg, raw instanceof Tag tag ? tag : encode(type, raw, registries), registries);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Reads a component from its saved (NBT) form, exactly as the owning mod's codec writes it. Returns null when
     * the data does not have the expected shape, so the container passes through.
     */
    @Nullable
    static List<ItemStack> readEncoded(String kind, @Nullable String arg, @Nullable Tag encoded, HolderLookup.Provider registries) {
        if (encoded == null) {
            return null;
        }
        List<ItemStack> out = new ArrayList<>();
        switch (kind) {
            case "item_container" -> {
                // ItemContainerContents: [{slot:int, item:{...}}, ...]
                if (!(encoded instanceof ListTag list)) {
                    return null;
                }
                for (Tag element : list) {
                    if (!(element instanceof CompoundTag slot) || !slot.contains("item", Tag.TAG_COMPOUND)) {
                        return null;
                    }
                    ItemStack parsed = parseStack(slot.getCompound("item"), registries);
                    if (parsed == null) {
                        return null;
                    }
                    addIfPresent(out, parsed);
                }
                return out;
            }
            case "single_stack" -> {
                if (!(encoded instanceof CompoundTag compound)) {
                    return null;
                }
                ItemStack parsed = parseStack(compound, registries);
                if (parsed == null) {
                    return null;
                }
                addIfPresent(out, parsed);
                return out;
            }
            case "stack_list" -> {
                Tag listTag = arg == null ? encoded : (encoded instanceof CompoundTag compound ? compound.get(arg) : null);
                if (arg != null && !(encoded instanceof CompoundTag)) {
                    return null;
                }
                return parseStackList(listTag, registries);
            }
            case "item_id_list" -> {
                if (!(encoded instanceof CompoundTag compound)) {
                    return null;
                }
                if (arg == null || !compound.contains(arg)) {
                    return out;
                }
                if (!(compound.get(arg) instanceof ListTag list)) {
                    return null;
                }
                for (Tag element : list) {
                    if (!(element instanceof StringTag string)) {
                        return null;
                    }
                    ResourceLocation itemId = ResourceLocation.tryParse(string.getAsString());
                    if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                        return null;
                    }
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR) {
                        out.add(new ItemStack(item));
                    }
                }
                return out;
            }
            default -> {
                return null;
            }
        }
    }

    /** Known custom-data inventories are read; anything else that looks like an item stack makes it pass through. */
    @Nullable
    private static List<ItemStack> readCustomData(ItemStack stack, CompoundTag data, HolderLookup.Provider registries) {
        String path = ContainerRules.get().customDataReaders.get(itemId(stack));
        List<ItemStack> out = new ArrayList<>();
        CompoundTag rest = data;
        if (path != null) {
            Tag at = ItemNbtScan.at(data, path);
            if (at != null) {
                List<ItemStack> read = parseStackList(at, registries);
                if (read == null) {
                    return null;
                }
                out.addAll(read);
            }
            rest = ItemNbtScan.without(data, path);
        }
        if (ItemNbtScan.containsItemStack(rest, ContainerContents::isItemId)) {
            return null;
        }
        return out;
    }

    @Nullable
    private static List<ItemStack> parseStackList(@Nullable Tag tag, HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        if (tag == null) {
            return out;
        }
        if (!(tag instanceof ListTag list)) {
            return null;
        }
        for (Tag element : list) {
            if (!(element instanceof CompoundTag compound)) {
                return null;
            }
            ItemStack parsed = parseStack(compound, registries);
            if (parsed == null) {
                return null;
            }
            addIfPresent(out, parsed);
        }
        return out;
    }

    /** Empty compound or air is an empty slot. A stack that does not parse is null (unreadable), never empty. */
    @Nullable
    static ItemStack parseStack(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.isEmpty() || "minecraft:air".equals(tag.getString("id"))) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parse(registries, tag).orElse(null);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> Tag encode(DataComponentType<T> type, Object value, HolderLookup.Provider registries) {
        Codec<T> codec = type.codec();
        if (codec == null) {
            return null;
        }
        try {
            DataResult<Tag> result = codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), (T) value);
            return result.result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * If the item exposes an item handler, it must report exactly what the components gave. A handler with
     * items the components did not explain (contents stored somewhere this mod cannot see) means pass through.
     */
    private static boolean capabilityAgrees(ItemStack stack, List<ItemStack> fromComponents) {
        IItemHandler handler;
        try {
            handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        } catch (RuntimeException e) {
            return false;
        }
        if (handler == null) {
            return true;
        }
        List<ItemStack> fromHandler = new ArrayList<>();
        try {
            copySlots(handler, fromHandler);
        } catch (RuntimeException e) {
            return false;
        }
        return sameItems(fromHandler, fromComponents);
    }

    /** Same items and components with the same total counts, ignoring order and stack splits. */
    public static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        List<ItemStack> left = merged(a);
        List<ItemStack> right = merged(b);
        if (left.size() != right.size()) {
            return false;
        }
        for (ItemStack l : left) {
            boolean found = false;
            for (ItemStack r : right) {
                if (ItemStack.isSameItemSameComponents(l, r) && l.getCount() == r.getCount()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> merged(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean added = false;
            for (ItemStack existing : out) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.setCount(existing.getCount() + stack.getCount());
                    added = true;
                    break;
                }
            }
            if (!added) {
                out.add(stack.copy());
            }
        }
        return out;
    }

    static void copySlots(IItemHandler handler, List<ItemStack> into) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack item = handler.getStackInSlot(slot);
            if (!item.isEmpty()) {
                into.add(item.copy());
            }
        }
    }

    private static void addIfPresent(List<ItemStack> out, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            out.add(stack);
        }
    }

    static boolean isItemId(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key) && BuiltInRegistries.ITEM.get(key) != Items.AIR;
    }

    static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return String.valueOf(key);
    }

    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void warnOnce(String key, String message, Throwable error) {
        if (WARNED.size() < 512 && WARNED.add(key)) {
            DisassemblyDelight.LOGGER.warn(message, error);
        }
    }

    /** Exposed for tests: the id predicate the generic guard uses. */
    public static Predicate<String> itemIdPredicate() {
        return ContainerContents::isItemId;
    }
}
