package ru.staskozin.spellroot.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import ru.staskozin.spellroot.Spellroot;
import ru.staskozin.spellroot.config.SpellrootConfig;
import ru.staskozin.spellroot.item.EnderFocusItem;
import ru.staskozin.spellroot.network.CancelEnderFocusUsePayload;
import ru.staskozin.spellroot.registry.ModItems;

@EventBusSubscriber(modid = Spellroot.MODID, value = Dist.CLIENT)
public final class EnderFocusClientEvents {
    private static boolean useBlockedUntilRelease;
    private static boolean queuedUse;
    private static int queuedSlot = -1;
    private static ItemStack queuedStack = ItemStack.EMPTY;

    private EnderFocusClientEvents() {
    }

    @SubscribeEvent
    private static void handleClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) {
            useBlockedUntilRelease = false;
            clearQueuedUse();
        }
        tryResumeQueuedUse(minecraft);
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

    private static boolean isAiming(LocalPlayer player) {
        return player != null
                && player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().is(ModItems.ENDER_FOCUS.get());
    }

}
