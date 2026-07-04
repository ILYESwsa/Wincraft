package com.wincraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** Renders captured desktop windows as simple textured quads in the world. */
public final class WincraftWorldRenderer {

    private static final float DEFAULT_WIDTH_BLOCKS = 2.5F;

    private WincraftWorldRenderer() {}

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WincraftWorldRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        if (WindowManager.get().all().isEmpty()) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        Camera camera = context.gameRenderer().getMainCamera();
        Vec3 cameraPos = camera.position();

        for (CapturedWindow window : WindowManager.get().all()) {
            if (!window.hasTexture()) {
                continue;
            }
            renderWindow(context, poseStack, cameraPos, window);
        }
    }

    private static void renderWindow(LevelRenderContext context, PoseStack poseStack, Vec3 cameraPos, CapturedWindow window) {
        float aspect = window.getTextureHeight() == 0 ? 1.0F : (float) window.getTextureWidth() / (float) window.getTextureHeight();
        float halfWidth = DEFAULT_WIDTH_BLOCKS * 0.5F;
        float halfHeight = halfWidth / Math.max(0.1F, aspect);

        poseStack.pushPose();
        poseStack.translate(window.getWorldX() - cameraPos.x, window.getWorldY() - cameraPos.y, window.getWorldZ() - cameraPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - window.getYawDegrees()));

        VertexConsumer vertices = context.bufferSource().getBuffer(RenderTypes.entityTranslucent(window.getTextureId()));
        addVertex(vertices, poseStack, -halfWidth, halfHeight, 0.0F, 0.0F, 0.0F);
        addVertex(vertices, poseStack, -halfWidth, -halfHeight, 0.0F, 0.0F, 1.0F);
        addVertex(vertices, poseStack, halfWidth, -halfHeight, 0.0F, 1.0F, 1.0F);
        addVertex(vertices, poseStack, halfWidth, halfHeight, 0.0F, 1.0F, 0.0F);
        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer vertices, PoseStack poseStack, float x, float y, float z, float u, float v) {
        vertices.addVertex(poseStack.last(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setLight(0x00F000F0)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}
