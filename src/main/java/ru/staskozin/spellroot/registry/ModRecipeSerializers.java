package ru.staskozin.spellroot.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.staskozin.spellroot.Spellroot;
import ru.staskozin.spellroot.recipe.EnderFocusRechargeRecipe;

public final class ModRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Spellroot.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<EnderFocusRechargeRecipe>> ENDER_FOCUS_RECHARGE =
            SERIALIZERS.register("ender_focus_recharge", () -> new RecipeSerializer<>(
                    MapCodec.unit(EnderFocusRechargeRecipe::new),
                    StreamCodec.unit(new EnderFocusRechargeRecipe())
            ));

    private ModRecipeSerializers() {
    }

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
