package ru.staskozin.spellroot.registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.staskozin.spellroot.Spellroot;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Spellroot.MODID);

    public static final DeferredItem<Item> ENDER_FOCUS = ITEMS.registerSimpleItem("ender_focus", properties -> properties
            .stacksTo(1)
            .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ENDER_FOCUS);
        }
    }
}
