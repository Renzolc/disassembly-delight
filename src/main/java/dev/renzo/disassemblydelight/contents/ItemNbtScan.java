package dev.renzo.disassemblydelight.contents;

import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Pure NBT checks used by the generic guard. No registries: the caller says which ids are items.
 * Kept free of game state so it can be unit tested without a running game.
 */
public final class ItemNbtScan {
    private static final int MAX_DEPTH = 64;
    /** Keys every block entity tag carries. Anything else means the block item kept state from the world. */
    private static final Set<String> BARE_STACK_KEYS = Set.of("id", "Slot", "slot");
    private static final Set<String> BLOCK_ENTITY_IDENTITY_KEYS = Set.of("id", "x", "y", "z");

    private ItemNbtScan() {
    }

    /**
     * True for an encoded item stack: {@code {id:"ns:item", count:N}} (1.20.5+), the old {@code Count} form,
     * or {@code {id, components}} written by the single-item codec.
     */
    public static boolean looksLikeItemStack(CompoundTag tag, Predicate<String> isItemId) {
        if (tag == null || !tag.contains("id", Tag.TAG_STRING)) {
            return false;
        }
        boolean counted = tag.contains("count", Tag.TAG_ANY_NUMERIC) || tag.contains("Count", Tag.TAG_ANY_NUMERIC)
                || tag.contains("components", Tag.TAG_COMPOUND);
        // A single item may be saved as just {id} (single-item codecs), optionally with a slot index.
        boolean bare = BARE_STACK_KEYS.containsAll(tag.getAllKeys());
        return (counted || bare) && isItemId.test(tag.getString("id"));
    }

    /** True if any compound anywhere inside the tag looks like an item stack. */
    public static boolean containsItemStack(Tag tag, Predicate<String> isItemId) {
        return containsItemStack(tag, isItemId, 0);
    }

    private static boolean containsItemStack(Tag tag, Predicate<String> isItemId, int depth) {
        if (tag == null) {
            return false;
        }
        if (depth > MAX_DEPTH) {
            // Absurdly deep data: assume the worst.
            return true;
        }
        if (tag instanceof CompoundTag compound) {
            if (looksLikeItemStack(compound, isItemId)) {
                return true;
            }
            for (String key : compound.getAllKeys()) {
                if (containsItemStack(compound.get(key), isItemId, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (tag instanceof ListTag list) {
            for (Tag element : list) {
                if (containsItemStack(element, isItemId, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Empty compound, empty list, or empty array: the component holds nothing. */
    public static boolean isEmptyData(Tag tag) {
        if (tag == null) {
            return true;
        }
        if (tag instanceof CompoundTag compound) {
            return compound.isEmpty();
        }
        if (tag instanceof CollectionTag<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    /**
     * A block item carries world state (inventory, record, book, fluid, meal...) when its block entity data has
     * more than the identity keys.
     */
    public static boolean blockEntityDataCarriesState(CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        for (String key : tag.getAllKeys()) {
            if (!BLOCK_ENTITY_IDENTITY_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the tag at a slash-separated path of compound keys ({@code "VoidSack/Items"}), or null.
     */
    public static Tag at(CompoundTag root, String path) {
        Tag current = root;
        for (String key : path.split("/")) {
            if (!(current instanceof CompoundTag compound) || !compound.contains(key)) {
                return null;
            }
            current = compound.get(key);
        }
        return current;
    }

    /** Copy of {@code root} without the tag at {@code path}. */
    public static CompoundTag without(CompoundTag root, String path) {
        CompoundTag copy = root.copy();
        String[] keys = path.split("/");
        Tag current = copy;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!(current instanceof CompoundTag compound) || !compound.contains(keys[i])) {
                return copy;
            }
            current = compound.get(keys[i]);
        }
        if (current instanceof CompoundTag compound) {
            compound.remove(keys[keys.length - 1]);
        }
        return copy;
    }
}
