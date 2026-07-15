package ru.staskozin.spellroot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import ru.staskozin.spellroot.registry.ModItems;

@Mod(Spellroot.MODID)
public final class Spellroot {
    public static final String MODID = "spellroot";

    public Spellroot(IEventBus modEventBus) {
        ModItems.register(modEventBus);
    }
}
