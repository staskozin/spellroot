package ru.staskozin.spellroot.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpellrootConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAX_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_CHARGES;
    public static final ModConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue LEDGE_ASSIST_BLOCKS;
    public static final ModConfigSpec.BooleanValue CREATIVE_CONSUMES_CHARGES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("gameplay");
        MAX_DISTANCE = builder
                .comment("Maximum blink distance in blocks.")
                .defineInRange("max_distance", 10.0, 1.0, 64.0);
        MAX_CHARGES = builder
                .comment("Maximum number of charges stored in an Ender Focus.")
                .defineInRange("max_charges", 8, 1, 64);
        COOLDOWN_TICKS = builder
                .comment("Shared player cooldown after a successful Ender Focus blink, in ticks.")
                .defineInRange("cooldown_ticks", 5, 0, 1200);
        LEDGE_ASSIST_BLOCKS = builder
                .comment("Maximum number of blocks checked above the block face hit by the aiming ray.")
                .defineInRange("ledge_assist_blocks", 2, 0, 8);
        CREATIVE_CONSUMES_CHARGES = builder
                .comment("Whether creative-mode players consume Ender Focus charges.")
                .define("creative_consumes_charges", false);
        builder.pop();
        SPEC = builder.build();
    }

    private SpellrootConfig() {
    }

    public static int maxCharges() {
        return SPEC.isLoaded() ? MAX_CHARGES.getAsInt() : 8;
    }

    public static double maxDistance() {
        return SPEC.isLoaded() ? MAX_DISTANCE.getAsDouble() : 10.0;
    }

    public static int cooldownTicks() {
        return SPEC.isLoaded() ? COOLDOWN_TICKS.getAsInt() : 5;
    }

    public static int ledgeAssistBlocks() {
        return SPEC.isLoaded() ? LEDGE_ASSIST_BLOCKS.getAsInt() : 2;
    }

    public static boolean creativeConsumesCharges() {
        return SPEC.isLoaded() && CREATIVE_CONSUMES_CHARGES.getAsBoolean();
    }
}
