package dev.renzo.disassemblydelight.recipe;

import java.util.function.Supplier;

import dev.renzo.disassemblydelight.DisassemblyDelight;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, DisassemblyDelight.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, DisassemblyDelight.MOD_ID);

    public static final Supplier<RecipeType<FullUncraftRecipe>> FULL_UNCRAFT = RECIPE_TYPES.register("full_uncraft",
            () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return DisassemblyDelight.MOD_ID + ":full_uncraft";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FullUncraftRecipe>> FULL_UNCRAFT_SERIALIZER =
            SERIALIZERS.register("full_uncraft", FullUncraftRecipe.Serializer::new);

    private ModRecipeTypes() {}
}
