package ru.staskozin.spellroot.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import ru.staskozin.spellroot.Spellroot;

@NullMarked
public record CancelEnderFocusUsePayload() implements CustomPacketPayload {
    public static final CancelEnderFocusUsePayload INSTANCE = new CancelEnderFocusUsePayload();
    public static final Type<CancelEnderFocusUsePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Spellroot.MODID, "cancel_ender_focus_use")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CancelEnderFocusUsePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
