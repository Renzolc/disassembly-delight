package dev.renzo.disassemblydelight.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Full-count uncraft used only by the Disassembler Upgrade.
 * Cutting-board recipes stay on their partial yields.
 */
public class FullUncraftRecipe implements Recipe<SingleRecipeInput> {
    private final Ingredient input;
    private final List<ItemStack> results;
    private final int consume;

    public FullUncraftRecipe(Ingredient input, List<ItemStack> results, int consume) {
        this.input = input;
        this.results = List.copyOf(results);
        this.consume = Math.max(1, consume);
    }

    public Ingredient input() {
        return input;
    }

    public List<ItemStack> results() {
        return results;
    }

    public int consume() {
        return consume;
    }

    public List<ItemStack> copyResults() {
        List<ItemStack> copy = new ArrayList<>(results.size());
        for (ItemStack stack : results) {
            if (!stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }
        return copy;
    }

    public static Optional<FullUncraftRecipe> find(Level level, ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return Optional.empty();
        }
        FullUncraftRecipe best = null;
        String bestId = null;
        for (RecipeHolder<FullUncraftRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.FULL_UNCRAFT.get())) {
            FullUncraftRecipe recipe = holder.value();
            if (!recipe.input.test(stack) || recipe.results.isEmpty()) {
                continue;
            }
            String id = holder.id().toString();
            if (best == null || id.compareTo(bestId) < 0) {
                best = recipe;
                bestId = id;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.getFirst().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.getFirst();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.FULL_UNCRAFT_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.FULL_UNCRAFT.get();
    }

    public static class Serializer implements RecipeSerializer<FullUncraftRecipe> {
        public static final MapCodec<FullUncraftRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("input").forGetter(FullUncraftRecipe::input),
                ItemStack.CODEC.listOf().fieldOf("results").forGetter(FullUncraftRecipe::results),
                Codec.INT.optionalFieldOf("consume", 1).forGetter(FullUncraftRecipe::consume)
        ).apply(instance, FullUncraftRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FullUncraftRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, FullUncraftRecipe::input,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), FullUncraftRecipe::results,
                ByteBufCodecs.VAR_INT, FullUncraftRecipe::consume,
                FullUncraftRecipe::new
        );

        @Override
        public MapCodec<FullUncraftRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FullUncraftRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    /** Unused, keeps the resource location obvious in crash reports. */
    @SuppressWarnings("unused")
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("disassembly_delight", "full_uncraft");
}
