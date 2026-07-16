package ru.staskozin.spellroot.registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.staskozin.spellroot.Spellroot;
import ru.staskozin.spellroot.component.EnderFocusState;
import ru.staskozin.spellroot.item.EnderFocusItem;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Spellroot.MODID);

    public static final DeferredItem<EnderFocusItem> ENDER_FOCUS = ITEMS.registerItem(
            "ender_focus",
            EnderFocusItem::new,
            properties -> properties
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .component(ModDataComponents.ENDER_FOCUS_STATE.get(), EnderFocusState.DEFAULT)
    );

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
