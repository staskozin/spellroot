package ru.staskozin.spellroot.registry;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.staskozin.spellroot.Spellroot;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Spellroot.MODID);

    public static final DeferredItem<Item> ENDER_FOCUS = ITEMS.registerSimpleItem("ender_focus");

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
