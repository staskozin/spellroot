package ru.staskozin.spellroot.item;

import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import ru.staskozin.spellroot.component.EnderFocusState;
import ru.staskozin.spellroot.config.SpellrootConfig;
import ru.staskozin.spellroot.gameplay.EnderFocusTargetResolver;
import ru.staskozin.spellroot.registry.ModDataComponents;

@NullMarked
public final class EnderFocusItem extends Item {
    private static final DustParticleOptions CYAN_DUST = new DustParticleOptions(0x35E1D1, 0.9F);
    private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(0x9B5DE5, 0.9F);

    public EnderFocusItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.isAlive() || player.isSpectator() || player.hasContainerOpen()) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = player.getItemInHand(hand);
        EnderFocusState state = normalizeState(stack);
        boolean hasCharge = player.hasInfiniteMaterials() && !SpellrootConfig.creativeConsumesCharges() || state.charges() > 0;
        if (!hasCharge) {
            if (player instanceof ServerPlayer serverPlayer) {
                playFailureSound(serverPlayer);
            }
            return InteractionResult.FAIL;
        }

        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return APPROXIMATELY_INFINITE_USE_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return false;
        }
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND
                || player.getMainHandItem() != stack
                || !player.isAlive()
                || player.isSpectator()
                || player.hasContainerOpen()) {
            playFailureSound(player);
            return false;
        }

        EnderFocusState state = normalizeState(stack);
        boolean consumesCharge = !player.hasInfiniteMaterials() || SpellrootConfig.creativeConsumesCharges();
        if (consumesCharge && state.charges() <= 0) {
            playFailureSound(player);
            return false;
        }

        EnderFocusTargetResolver.Result target = EnderFocusTargetResolver.resolve(
                level,
                player,
                SpellrootConfig.maxDistance(),
                SpellrootConfig.ledgeAssistBlocks()
        );
        if (!target.valid()) {
            playFailureSound(player);
            return false;
        }

        Vec3 origin = player.position();
        if (player.isPassenger()) {
            player.stopRiding();
        }

        Vec3 destination = target.feetPosition();
        boolean teleported = player.teleportTo(
                serverLevel,
                destination.x,
                destination.y,
                destination.z,
                Set.of(),
                player.getYRot(),
                player.getXRot(),
                false
        );
        if (!teleported) {
            playFailureSound(player);
            return false;
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        EnderFocusState updatedState = stateAfterSuccessfulBlink(state, player);
        stack.set(ModDataComponents.ENDER_FOCUS_STATE.get(), updatedState);
        player.awardStat(Stats.ITEM_USED.get(this));
        player.gameEvent(GameEvent.TELEPORT);
        playSuccessEffects(serverLevel, origin, destination);
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        normalizeState(stack);
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        if (!level.isClientSide()) {
            int maxCharges = SpellrootConfig.maxCharges();
            stack.set(ModDataComponents.ENDER_FOCUS_STATE.get(), new EnderFocusState(
                    maxCharges,
                    maxCharges
            ));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        EnderFocusState state = getState(stack);
        return state.charges() < state.maxCharges();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        EnderFocusState state = getState(stack);
        return Math.round(13.0F * state.charges() / state.maxCharges());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        EnderFocusState state = getState(stack);
        float ratio = (float) state.charges() / state.maxCharges();
        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag tooltipFlag
    ) {
        EnderFocusState state = getState(stack);
        builder.accept(Component.translatable(
                "item.spellroot.ender_focus.charges",
                state.charges(),
                state.maxCharges()
        ).withStyle(ChatFormatting.GRAY));
    }

    public static EnderFocusState getState(ItemStack stack) {
        EnderFocusState state = stack.get(ModDataComponents.ENDER_FOCUS_STATE.get());
        return state == null ? EnderFocusState.DEFAULT : state;
    }

    public static EnderFocusState normalizeState(ItemStack stack) {
        if (!stack.has(ModDataComponents.ENDER_FOCUS_STATE.get())) {
            EnderFocusState fullState = EnderFocusState.full(SpellrootConfig.maxCharges());
            stack.set(ModDataComponents.ENDER_FOCUS_STATE.get(), fullState);
            return fullState;
        }
        EnderFocusState current = getState(stack);
        EnderFocusState normalized = current.normalize(SpellrootConfig.maxCharges());
        if (!normalized.equals(current)) {
            stack.set(ModDataComponents.ENDER_FOCUS_STATE.get(), normalized);
        }
        return normalized;
    }

    public static EnderFocusState stateAfterSuccessfulBlink(EnderFocusState state, Player player) {
        boolean consumesCharge = !player.hasInfiniteMaterials() || SpellrootConfig.creativeConsumesCharges();
        return consumesCharge ? state.withCharges(state.charges() - 1) : state;
    }

    private static void playFailureSound(ServerPlayer player) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_RESONATE),
                SoundSource.PLAYERS,
                player.getX(),
                player.getY(),
                player.getZ(),
                0.25F,
                0.55F,
                player.getRandom().nextLong()
        ));
    }

    private static void playSuccessEffects(ServerLevel level, Vec3 origin, Vec3 destination) {
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.15F);
        level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.35F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, origin.x, origin.y + 0.9, origin.z, 18, 0.25, 0.8, 0.25, 0.05);
        level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + 0.9, destination.z, 18, 0.25, 0.8, 0.25, 0.05);
        level.sendParticles(CYAN_DUST, destination.x, destination.y + 0.05, destination.z, 7, 0.3, 0.02, 0.3, 0.0);
        level.sendParticles(PURPLE_DUST, origin.x, origin.y + 0.05, origin.z, 7, 0.3, 0.02, 0.3, 0.0);

        Vec3 delta = destination.subtract(origin);
        for (int step = 1; step <= 5; step++) {
            Vec3 point = origin.add(delta.scale(step / 6.0));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y + 0.8, point.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }
}
