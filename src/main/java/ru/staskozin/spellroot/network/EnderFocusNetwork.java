package ru.staskozin.spellroot.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.staskozin.spellroot.registry.ModItems;

public final class EnderFocusNetwork {
    private EnderFocusNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                CancelEnderFocusUsePayload.TYPE,
                CancelEnderFocusUsePayload.STREAM_CODEC,
                EnderFocusNetwork::handleCancelUse
        );
    }

    private static void handleCancelUse(
            @SuppressWarnings("unused") CancelEnderFocusUsePayload payload,
            IPayloadContext context
    ) {
        if (context.player() instanceof ServerPlayer player
                && player.isUsingItem()
                && player.getUseItem().is(ModItems.ENDER_FOCUS.get())) {
            player.stopUsingItem();
        }
    }
}
