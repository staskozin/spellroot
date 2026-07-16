package ru.staskozin.spellroot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import ru.staskozin.spellroot.config.SpellrootConfig;
import ru.staskozin.spellroot.gametest.ModGameTests;
import ru.staskozin.spellroot.network.EnderFocusNetwork;
import ru.staskozin.spellroot.registry.ModDataComponents;
import ru.staskozin.spellroot.registry.ModItems;
import ru.staskozin.spellroot.registry.ModRecipeSerializers;

@Mod(Spellroot.MODID)
public final class Spellroot {
    public static final String MODID = "spellroot";

    public Spellroot(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModGameTests.register(modEventBus);
        modEventBus.addListener(EnderFocusNetwork::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.SERVER, SpellrootConfig.SPEC);
    }
}
