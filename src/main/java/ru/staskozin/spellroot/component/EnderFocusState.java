package ru.staskozin.spellroot.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record EnderFocusState(int charges, int maxCharges) {
    public static final int DEFAULT_MAX_CHARGES = 8;
    public static final EnderFocusState DEFAULT = full(DEFAULT_MAX_CHARGES);

    public static final Codec<EnderFocusState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("charges").forGetter(EnderFocusState::charges),
            Codec.INT.fieldOf("max_charges").forGetter(EnderFocusState::maxCharges)
    ).apply(instance, EnderFocusState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnderFocusState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EnderFocusState::charges,
            ByteBufCodecs.VAR_INT, EnderFocusState::maxCharges,
            EnderFocusState::new
    );

    public static EnderFocusState full(int maxCharges) {
        int normalizedMax = Math.max(1, maxCharges);
        return new EnderFocusState(normalizedMax, normalizedMax);
    }

    public EnderFocusState normalize(int configuredMaxCharges) {
        int normalizedMax = Math.max(1, configuredMaxCharges);
        int normalizedCharges = Mth.clamp(this.charges, 0, normalizedMax);
        return new EnderFocusState(normalizedCharges, normalizedMax);
    }

    public EnderFocusState withCharges(int charges) {
        return new EnderFocusState(Mth.clamp(charges, 0, this.maxCharges), this.maxCharges);
    }
}
