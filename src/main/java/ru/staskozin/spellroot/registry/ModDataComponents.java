package ru.staskozin.spellroot.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.staskozin.spellroot.Spellroot;
import ru.staskozin.spellroot.component.EnderFocusState;

public final class ModDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Spellroot.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnderFocusState>> ENDER_FOCUS_STATE =
            COMPONENTS.registerComponentType("ender_focus_state", builder -> builder
                    .persistent(EnderFocusState.CODEC)
                    .networkSynchronized(EnderFocusState.STREAM_CODEC)
                    .cacheEncoding());

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
