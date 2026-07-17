package ru.staskozin.spellroot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import ru.staskozin.spellroot.Spellroot;
import ru.staskozin.spellroot.config.SpellrootConfig;
import ru.staskozin.spellroot.gameplay.EnderFocusTargetResolver;
import ru.staskozin.spellroot.item.EnderFocusItem;
import ru.staskozin.spellroot.network.CancelEnderFocusUsePayload;
import ru.staskozin.spellroot.registry.ModItems;

@EventBusSubscriber(modid = Spellroot.MODID, value = Dist.CLIENT)
public final class EnderFocusClientEvents {
    private static final ContextKey<MarkerState> MARKER_STATE = new ContextKey<>(
            Identifier.fromNamespaceAndPath(Spellroot.MODID, "ender_focus_marker")
    );
    private static final DustParticleOptions CYAN_DUST = new DustParticleOptions(0x35E1D1, 0.75F);
    private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(0x9B5DE5, 0.75F);
    private static final DustParticleOptions RED_DUST = new DustParticleOptions(0xEF3340, 0.75F);
    private static final int VALID_COLOR = 0xCC35E1D1;
    private static final int INVALID_COLOR = 0xCCEF3340;
    private static boolean useBlockedUntilRelease;
    private static boolean queuedUse;
    private static int queuedSlot = -1;
    private static ItemStack queuedStack = ItemStack.EMPTY;

    private EnderFocusClientEvents() {
    }

    @SubscribeEvent
    private static void extractMarker(ExtractLevelRenderStateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!isAiming(player) || Minecraft.getInstance().gui.screen() != null) {
            return;
        }

        EnderFocusTargetResolver.Result result = resolve(event.getLevel(), player);
        event.getRenderState().setRenderData(MARKER_STATE, new MarkerState(
                result.feetPosition(),
                player.getDimensions(player.getPose()).width(),
                player.getDimensions(player.getPose()).height(),
                result.valid()
        ));
    }

    @SubscribeEvent
    private static void submitMarker(SubmitCustomGeometryEvent event) {
        MarkerState marker = event.getLevelRenderState().getRenderData(MARKER_STATE);
        if (marker == null) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        double halfWidth = marker.width() / 2.0;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                marker.position().x - camera.x,
                marker.position().y - camera.y + 0.01,
                marker.position().z - camera.z
        );
        event.getSubmitNodeCollector().submitShapeOutline(
                poseStack,
                Shapes.box(-halfWidth, 0.0, -halfWidth, halfWidth, marker.height(), halfWidth),
                RenderTypes.lines(),
                marker.valid() ? VALID_COLOR : INVALID_COLOR,
                2.0F,
                false
        );
        poseStack.popPose();
    }

    @SubscribeEvent
    private static void spawnMarkerParticles(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) {
            useBlockedUntilRelease = false;
            clearQueuedUse();
        }
        tryResumeQueuedUse(minecraft);

        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || minecraft.gui.screen() != null || !isAiming(player) || player.tickCount % 3 != 0) {
            return;
        }

        EnderFocusTargetResolver.Result result = resolve(level, player);
        double radius = player.getDimensions(player.getPose()).width() / 2.0;
        DustParticleOptions first = result.valid() ? CYAN_DUST : RED_DUST;
        DustParticleOptions second = result.valid() ? PURPLE_DUST : RED_DUST;
        Vec3 position = result.feetPosition().add(0.0, 0.04, 0.0);
        addParticle(level, first, position.add(radius, 0.0, 0.0));
        addParticle(level, second, position.add(-radius, 0.0, 0.0));
        addParticle(level, first, position.add(0.0, 0.0, radius));
        addParticle(level, second, position.add(0.0, 0.0, -radius));
    }

    @SubscribeEvent
    private static void cancelWhenScreenOpens(ScreenEvent.Opening event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getNewScreen() != null) {
            if (queuedUse || isAiming(player)) {
                useBlockedUntilRelease = true;
            }
            clearQueuedUse();
            if (isAiming(player)) {
                ClientPacketDistributor.sendToServer(CancelEnderFocusUsePayload.INSTANCE);
                player.stopUsingItem();
            }
        }
    }

    @SubscribeEvent
    private static void cancelOnLeftClick(InputEvent.MouseButton.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getButton() == 0
                && event.getAction() == InputConstants.PRESS
                && (isAiming(player) || queuedUse)) {
            useBlockedUntilRelease = true;
            clearQueuedUse();
            if (isAiming(player)) {
                ClientPacketDistributor.sendToServer(CancelEnderFocusUsePayload.INSTANCE);
                player.stopUsingItem();
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    private static void blockUseUntilRelease(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        if (useBlockedUntilRelease || queuedUse) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getHand() == InteractionHand.MAIN_HAND && shouldQueueUse(player)) {
            queuedUse = true;
            queuedSlot = player.getInventory().getSelectedSlot();
            queuedStack = player.getMainHandItem().copy();
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static boolean shouldQueueUse(LocalPlayer player) {
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || player.hasContainerOpen()) {
            return false;
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.ENDER_FOCUS.get()) || !player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }

        return player.hasInfiniteMaterials() && !SpellrootConfig.creativeConsumesCharges()
                || EnderFocusItem.getState(stack).charges() > 0;
    }

    private static void tryResumeQueuedUse(Minecraft minecraft) {
        if (!queuedUse) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.level == null
                || minecraft.gameMode == null
                || minecraft.gui.screen() != null
                || !player.isAlive()
                || player.isSpectator()
                || player.hasContainerOpen()
                || player.isUsingItem()
                || player.getInventory().getSelectedSlot() != queuedSlot
                || !ItemStack.matches(player.getMainHandItem(), queuedStack)) {
            useBlockedUntilRelease = true;
            clearQueuedUse();
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (player.getCooldowns().isOnCooldown(stack)) {
            return;
        }

        clearQueuedUse();
        minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }

    private static void clearQueuedUse() {
        queuedUse = false;
        queuedSlot = -1;
        queuedStack = ItemStack.EMPTY;
    }

    private static EnderFocusTargetResolver.Result resolve(net.minecraft.world.level.Level level, LocalPlayer player) {
        return EnderFocusTargetResolver.resolve(
                level,
                player,
                SpellrootConfig.maxDistance(),
                SpellrootConfig.ledgeAssistBlocks()
        );
    }

    private static boolean isAiming(LocalPlayer player) {
        return player != null
                && player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().is(ModItems.ENDER_FOCUS.get());
    }

    private static void addParticle(ClientLevel level, DustParticleOptions particle, Vec3 position) {
        level.addParticle(particle, position.x, position.y, position.z, 0.0, 0.005, 0.0);
    }

    private record MarkerState(Vec3 position, double width, double height, boolean valid) {
    }
}
