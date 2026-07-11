package com.wincraft.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wincraft.Wincraft;
import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Renders captured desktop windows as simple textured quads in the world. */
public final class WincraftWorldRenderer {

    // A single 1x1 black pixel, reused as the "back of the window" texture
    // for every CapturedWindow. Drawn with the same entityCutout render
    // type as the front face (rather than hunting for a bare "solid, no
    // texture" RenderTypes entry, which isn't part of any confirmed-stable
    // public API surface for 26.1) so both faces use one code path that's
    // already known to compile and render correctly in this environment.
    private static final Identifier BACK_TEXTURE_ID = Identifier.fromNamespaceAndPath(Wincraft.MOD_ID, "capture_back");
    private static boolean backTextureRegistered = false;

    private WincraftWorldRenderer() {}

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WincraftWorldRenderer::render);
    }

    private static void ensureBackTexture() {
        if (backTextureRegistered) return;
        NativeImage pixel = new NativeImage(1, 1, false);
        pixel.setPixelABGR(0, 0, 0xFF000000); // opaque black, ABGR
        DynamicTexture texture = new DynamicTexture(() -> "wincraft/capture_back", pixel);
        Minecraft.getInstance().getTextureManager().register(BACK_TEXTURE_ID, texture);
        texture.upload();
        backTextureRegistered = true;
    }

    private static void render(LevelRenderContext context) {
        if (WindowManager.get().all().isEmpty()) {
            return;
        }

        ensureBackTexture();

        // Poll for fresh frames here, once per rendered frame, rather than
        // only from the 20Hz client tick (see WincraftClient#onClientTick).
        // The tick loop runs at a fixed 20/sec regardless of the game's
        // actual framerate, which capped captured windows at a choppy
        // ~20fps ceiling even when the rest of the game ran much faster.
        // Polling here lets updates track real render framerate instead.
        for (CapturedWindow window : WindowManager.get().all()) {
            window.update();
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
        float halfWidth = window.getWidthBlocks() * 0.5F;
        float halfHeight = halfWidth / Math.max(0.1F, aspect);

        poseStack.pushPose();
        poseStack.translate(window.getWorldX() - cameraPos.x, window.getWorldY() - cameraPos.y, window.getWorldZ() - cameraPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - window.getYawDegrees()));

        // Front face: the captured window texture. entityCutout (not
        // translucent) since the captured frame is always fully opaque
        // now (see CapturedWindow#uploadFrame) — this also gives correct
        // depth-sorting against terrain instead of translucent blend order.
        VertexConsumer vertices = context.bufferSource().getBuffer(RenderTypes.entityCutout(window.getTextureId()));
        addVertex(vertices, poseStack, -halfWidth, halfHeight, 0.0F, 0.0F, 0.0F);
        addVertex(vertices, poseStack, -halfWidth, -halfHeight, 0.0F, 0.0F, 1.0F);
        addVertex(vertices, poseStack, halfWidth, -halfHeight, 0.0F, 1.0F, 1.0F);
        addVertex(vertices, poseStack, halfWidth, halfHeight, 0.0F, 1.0F, 0.0F);

        // Back face: solid black, wound the opposite way so it faces the
        // other direction, and pushed back by a small epsilon along the
        // normal. Without this offset both quads sit at the exact same
        // depth, and depth-buffer precision can't consistently pick a
        // winner up close / at grazing angles — causing the flicker
        // between the texture and black that shows up at close range.
        float backOffset = 0.01F;
        VertexConsumer backVertices = context.bufferSource().getBuffer(RenderTypes.entityCutout(BACK_TEXTURE_ID));
        addVertex(backVertices, poseStack, -halfWidth, halfHeight, -backOffset, 0.0F, 0.0F);
        addVertex(backVertices, poseStack, halfWidth, halfHeight, -backOffset, 0.0F, 0.0F);
        addVertex(backVertices, poseStack, halfWidth, -halfHeight, -backOffset, 0.0F, 0.0F);
        addVertex(backVertices, poseStack, -halfWidth, -halfHeight, -backOffset, 0.0F, 0.0F);

        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer vertices, PoseStack poseStack, float x, float y, float z, float u, float v) {
        vertices.addVertex(poseStack.last(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setUv1(0, 0)
                .setLight(0x00F000F0)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}