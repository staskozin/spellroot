package ru.staskozin.spellroot.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import org.joml.Matrix4f;
import ru.staskozin.spellroot.Spellroot;
import ru.staskozin.spellroot.config.SpellrootConfig;
import ru.staskozin.spellroot.gameplay.EnderFocusTargetResolver;
import ru.staskozin.spellroot.registry.ModItems;

@EventBusSubscriber(modid = Spellroot.MODID, value = Dist.CLIENT)
public final class EnderFocusMarkerRenderer {
    private static final ContextKey<MarkerState> MARKER_STATE = new ContextKey<>(
            Identifier.fromNamespaceAndPath(Spellroot.MODID, "ender_focus_marker")
    );
    private static final DustColorTransitionOptions VALID_DUST =
            new DustColorTransitionOptions(0xE2C4FF, 0x7C3AED, 0.55F);
    private static final DustParticleOptions INVALID_DUST = new DustParticleOptions(0xEF3340, 0.55F);
    private static final int VALID_CORE_RGB = 0xCFA7FF;
    private static final int VALID_GLOW_RGB = 0x7C3AED;
    private static final int INVALID_CORE_RGB = 0xFF7A85;
    private static final int INVALID_GLOW_RGB = 0xEF3340;
    private static final int VALID_DEBUG_COLOR = 0xCC9B5DE5;
    private static final int INVALID_DEBUG_COLOR = 0xCCEF3340;
    private static final int BEAM_SECTIONS = 8;
    private static final float FLOOR_OFFSET = 0.0125F;
    private static final float PULSE_PERIOD_TICKS = 32.0F;
    private static final float ROTATION_PERIOD_TICKS = 196.0F;
    private static final float PULSE_AMOUNT = 0.05F;
    private static final float FRAME_HEIGHT_RATIO = 0.45F;
    private static final float FRAME_THICKNESS = 0.018F;
    private static final float CORE_RADIUS = 0.075F;
    private static final float GLOW_RADIUS = 0.27F;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float SQRT_TWO = (float) Math.sqrt(2.0);
    private static final int MARKER_VERTEX_BUFFER_SIZE = 4096;
    private static GpuBuffer markerVertexBuffer;

    private EnderFocusMarkerRenderer() {
    }

    @SubscribeEvent
    private static void extractMarker(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (isNotAiming(player) || minecraft.gui.screen() != null) {
            return;
        }

        EnderFocusTargetResolver.Result result = resolve(event.getLevel(), player);
        event.getRenderState().setRenderData(MARKER_STATE, new MarkerState(
                result.feetPosition(),
                player.getDimensions(player.getPose()).width(),
                player.getDimensions(player.getPose()).height(),
                result.valid(),
                event.getLevel().getGameTime() % 1024L
                        + event.getDeltaTracker().getGameTimeDeltaPartialTick(false),
                minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)
        ));
    }

    @SubscribeEvent
    private static void submitMarker(SubmitCustomGeometryEvent event) {
        MarkerState marker = event.getLevelRenderState().getRenderData(MARKER_STATE);
        if (marker == null || !marker.showDebugFrame()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                marker.position().x - camera.x,
                marker.position().y - camera.y,
                marker.position().z - camera.z
        );

        double halfWidth = marker.width() / 2.0;
        poseStack.translate(0.0, FLOOR_OFFSET, 0.0);
        event.getSubmitNodeCollector().submitShapeOutline(
                poseStack,
                Shapes.box(-halfWidth, 0.0, -halfWidth, halfWidth, marker.height(), halfWidth),
                RenderTypes.lines(),
                marker.valid() ? VALID_DEBUG_COLOR : INVALID_DEBUG_COLOR,
                2.0F,
                false
        );
        poseStack.popPose();
    }

    @SubscribeEvent
    private static void renderMarkerAfterTransparentLayers(RenderLevelStageEvent.AfterLevel event) {
        MarkerState marker = event.getLevelRenderState().getRenderData(MARKER_STATE);
        if (marker == null) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = new PoseStack();
        poseStack.translate(
                marker.position().x - camera.x,
                marker.position().y - camera.y,
                marker.position().z - camera.z
        );

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(MARKER_VERTEX_BUFFER_SIZE)) {
            BufferBuilder buffer = new BufferBuilder(
                    byteBuffer,
                    PrimitiveTopology.QUADS,
                    DefaultVertexFormat.POSITION_COLOR
            );
            renderMarker(poseStack.last(), buffer, marker);

            try (MeshData mesh = buffer.buildOrThrow()) {
                uploadAndDrawMarker(
                        Minecraft.getInstance().gameRenderer.mainRenderTarget(),
                        mesh,
                        new Matrix4f(event.getModelViewMatrix())
                );
            }
        }
    }

    private static void uploadAndDrawMarker(RenderTarget target, MeshData mesh, Matrix4f modelViewMatrix) {
        GpuTextureView colorTexture = target.getColorTextureView();
        GpuTextureView depthTexture = target.getDepthTextureView();
        if (colorTexture == null || depthTexture == null) {
            return;
        }

        int vertexBytes = mesh.vertexBuffer().remaining();
        if (markerVertexBuffer == null || markerVertexBuffer.size() < vertexBytes) {
            if (markerVertexBuffer != null) {
                markerVertexBuffer.close();
            }
            markerVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Ender Focus marker vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    Math.max(MARKER_VERTEX_BUFFER_SIZE, vertexBytes)
            );
        }

        RenderSystem.getDevice()
                .createCommandEncoder()
                .writeToBuffer(markerVertexBuffer.slice(0, vertexBytes), mesh.vertexBuffer());

        AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewMatrix);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Ender Focus marker",
                        colorTexture,
                        Optional.empty(),
                        depthTexture,
                        OptionalDouble.empty()
                )) {
            renderPass.setPipeline(EnderFocusRenderTypes.marker());
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, markerVertexBuffer.slice(0, vertexBytes));
            renderPass.setIndexBuffer(indexBuffer, indices.type());
            renderPass.drawIndexed(mesh.drawState().indexCount(), 1, 0, 0, 0);
        }
    }

    @SubscribeEvent
    private static void closeMarkerBuffer(ClientStoppingEvent event) {
        if (markerVertexBuffer != null) {
            markerVertexBuffer.close();
            markerVertexBuffer = null;
        }
    }

    @SubscribeEvent
    private static void spawnMarkerParticles(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null
                || minecraft.gui.screen() != null
                || isNotAiming(player)
                || player.tickCount % 2 != 0) {
            return;
        }

        EnderFocusTargetResolver.Result result = resolve(level, player);
        RandomSource random = level.getRandom();
        int count = 1 + random.nextInt(2);
        double spread = player.getDimensions(player.getPose()).width() * 0.18;
        Vec3 origin = result.feetPosition().add(0.0, 0.04, 0.0);

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = random.nextDouble() * spread;
            double x = origin.x + Math.cos(angle) * radius;
            double y = origin.y + random.nextDouble() * 0.18;
            double z = origin.z + Math.sin(angle) * radius;
            double upwardSpeed = 0.012 + random.nextDouble() * 0.018;
            level.addParticle(
                    result.valid() ? VALID_DUST : INVALID_DUST,
                    x,
                    y,
                    z,
                    0.0,
                    upwardSpeed,
                    0.0
            );
        }
    }

    private static void renderMarker(PoseStack.Pose pose, VertexConsumer buffer, MarkerState marker) {
        int coreRgb = marker.valid() ? VALID_CORE_RGB : INVALID_CORE_RGB;
        int glowRgb = marker.valid() ? VALID_GLOW_RGB : INVALID_GLOW_RGB;
        float pulse = 1.0F + PULSE_AMOUNT * Mth.sin(marker.animationTime() * TWO_PI / PULSE_PERIOD_TICKS);
        float rotation = marker.animationTime() * TWO_PI / ROTATION_PERIOD_TICKS;

        renderBeam(pose, buffer, marker.height(), pulse, marker.animationTime(), rotation, coreRgb, glowRgb);
        renderLowerFrame(pose, buffer, marker.width(), marker.height(), pulse, rotation, coreRgb, glowRgb);
    }

    private static void renderBeam(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float height,
            float pulse,
            float animationTime,
            float rotation,
            int coreRgb,
            int glowRgb
    ) {
        float coreRadius = CORE_RADIUS * pulse;
        float glowRadius = GLOW_RADIUS * pulse;
        renderPrismSection(pose, buffer, FLOOR_OFFSET, height, coreRadius, rotation, ARGB.color(185, coreRgb));

        for (int section = 0; section < BEAM_SECTIONS; section++) {
            float y0 = FLOOR_OFFSET + height * section / BEAM_SECTIONS;
            float y1 = FLOOR_OFFSET + height * (section + 1) / BEAM_SECTIONS;
            float middle = (section + 0.5F) / BEAM_SECTIONS;
            float flow = 0.5F + 0.5F * Mth.sin(animationTime * 0.45F - middle * TWO_PI * 1.5F);
            float topFade = 1.0F - middle * 0.62F;
            int alpha = Mth.clamp(Mth.floor((26.0F + flow * 50.0F) * topFade), 12, 76);
            renderPrismSection(pose, buffer, y0, y1, glowRadius, rotation, ARGB.color(alpha, glowRgb));
        }
    }

    private static void renderLowerFrame(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float width,
            float height,
            float pulse,
            float rotation,
            int coreRgb,
            int glowRgb
    ) {
        float halfWidth = width * 0.5F * pulse;
        float glowRadius = GLOW_RADIUS * pulse;
        float frameThickness = Math.max(FRAME_THICKNESS * pulse, halfWidth - glowRadius);
        float columnOffset = halfWidth - frameThickness * 0.5F;
        float topY = FLOOR_OFFSET + height * FRAME_HEIGHT_RATIO;
        int bottomColor = ARGB.color(85, coreRgb);
        int topColor = ARGB.color(3, glowRgb);

        renderBottomFrame(pose, buffer, halfWidth, frameThickness, rotation, bottomColor);
        renderGradientColumn(pose, buffer, -columnOffset, -columnOffset, frameThickness, topY, rotation, bottomColor, topColor);
        renderGradientColumn(pose, buffer, columnOffset, -columnOffset, frameThickness, topY, rotation, bottomColor, topColor);
        renderGradientColumn(pose, buffer, columnOffset, columnOffset, frameThickness, topY, rotation, bottomColor, topColor);
        renderGradientColumn(pose, buffer, -columnOffset, columnOffset, frameThickness, topY, rotation, bottomColor, topColor);
    }

    private static void renderBottomFrame(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float halfWidth,
            float thickness,
            float rotation,
            int color
    ) {
        float inner = halfWidth - thickness;
        float y = FLOOR_OFFSET + 0.001F;
        renderHorizontalQuad(pose, buffer, -halfWidth, -halfWidth, halfWidth, -inner, y, rotation, color);
        renderHorizontalQuad(pose, buffer, -halfWidth, inner, halfWidth, halfWidth, y, rotation, color);
        renderHorizontalQuad(pose, buffer, -halfWidth, -inner, -inner, inner, y, rotation, color);
        renderHorizontalQuad(pose, buffer, inner, -inner, halfWidth, inner, y, rotation, color);
    }

    private static void renderHorizontalQuad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float minX,
            float minZ,
            float maxX,
            float maxZ,
            float y,
            float rotation,
            int color
    ) {
        addRotatedVertex(pose, buffer, minX, y, minZ, rotation, color);
        addRotatedVertex(pose, buffer, maxX, y, minZ, rotation, color);
        addRotatedVertex(pose, buffer, maxX, y, maxZ, rotation, color);
        addRotatedVertex(pose, buffer, minX, y, maxZ, rotation, color);

        addRotatedVertex(pose, buffer, minX, y, maxZ, rotation, color);
        addRotatedVertex(pose, buffer, maxX, y, maxZ, rotation, color);
        addRotatedVertex(pose, buffer, maxX, y, minZ, rotation, color);
        addRotatedVertex(pose, buffer, minX, y, minZ, rotation, color);
    }

    private static void renderGradientColumn(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float centerX,
            float centerZ,
            float thickness,
            float topY,
            float rotation,
            int bottomColor,
            int topColor
    ) {
        float radius = thickness * 0.5F;
        renderGradientVerticalQuad(
                pose, buffer, centerX - radius, centerZ - radius, centerX + radius, centerZ - radius, topY, rotation, bottomColor, topColor
        );
        renderGradientVerticalQuad(
                pose, buffer, centerX + radius, centerZ - radius, centerX + radius, centerZ + radius, topY, rotation, bottomColor, topColor
        );
        renderGradientVerticalQuad(
                pose, buffer, centerX + radius, centerZ + radius, centerX - radius, centerZ + radius, topY, rotation, bottomColor, topColor
        );
        renderGradientVerticalQuad(
                pose, buffer, centerX - radius, centerZ + radius, centerX - radius, centerZ - radius, topY, rotation, bottomColor, topColor
        );
    }

    private static void renderGradientVerticalQuad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x0,
            float z0,
            float x1,
            float z1,
            float topY,
            float rotation,
            int bottomColor,
            int topColor
    ) {
        addRotatedVertex(pose, buffer, x0, topY, z0, rotation, topColor);
        addRotatedVertex(pose, buffer, x1, topY, z1, rotation, topColor);
        addRotatedVertex(pose, buffer, x1, FLOOR_OFFSET, z1, rotation, bottomColor);
        addRotatedVertex(pose, buffer, x0, FLOOR_OFFSET, z0, rotation, bottomColor);
    }

    private static void addRotatedVertex(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x,
            float y,
            float z,
            float rotation,
            int color
    ) {
        float cos = Mth.cos(rotation);
        float sin = Mth.sin(rotation);
        addVertex(
                pose,
                buffer,
                x * cos - z * sin,
                y,
                x * sin + z * cos,
                color
        );
    }

    private static void renderPrismSection(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float y0,
            float y1,
            float radius,
            float rotation,
            int color
    ) {
        float cornerRadius = radius * SQRT_TWO;
        for (int side = 0; side < 4; side++) {
            float angle = rotation - TWO_PI * 3.0F / 8.0F + TWO_PI * side / 4.0F;
            float nextAngle = angle + TWO_PI / 4.0F;
            renderVerticalQuad(
                    pose,
                    buffer,
                    Mth.cos(angle) * cornerRadius,
                    Mth.sin(angle) * cornerRadius,
                    Mth.cos(nextAngle) * cornerRadius,
                    Mth.sin(nextAngle) * cornerRadius,
                    y0,
                    y1,
                    color
            );
        }
    }

    private static void renderVerticalQuad(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x0,
            float z0,
            float x1,
            float z1,
            float y0,
            float y1,
            int color
    ) {
        addVertex(pose, buffer, x0, y1, z0, color);
        addVertex(pose, buffer, x1, y1, z1, color);
        addVertex(pose, buffer, x1, y0, z1, color);
        addVertex(pose, buffer, x0, y0, z0, color);
    }

    private static void addVertex(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x,
            float y,
            float z,
            int color
    ) {
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    private static EnderFocusTargetResolver.Result resolve(ClientLevel level, LocalPlayer player) {
        return EnderFocusTargetResolver.resolve(
                level,
                player,
                SpellrootConfig.maxDistance(),
                SpellrootConfig.ledgeAssistBlocks()
        );
    }

    private static boolean isNotAiming(LocalPlayer player) {
        return player == null
                || !player.isUsingItem()
                || player.getUsedItemHand() != InteractionHand.MAIN_HAND
                || !player.getUseItem().is(ModItems.ENDER_FOCUS.get());
    }

    private record MarkerState(
            Vec3 position,
            float width,
            float height,
            boolean valid,
            float animationTime,
            boolean showDebugFrame
    ) {
    }
}
