package com.wincraft.window;

import com.mojang.blaze3d.platform.NativeImage;
import com.wincraft.Wincraft;
import com.wincraft.natives.InputMode;
import com.wincraft.natives.VirtualKeyMap;
import com.wincraft.natives.WincraftNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Represents one live captured desktop window: owns the native capture
 * session and forwards input while the window is "focused".
 *
 * Captured BGRA8 frames are converted into a DynamicTexture for the
 * level renderer. The current upload path favors correctness over speed:
 * it copies pixels in Java and can be optimized later with a lower-level
 * direct upload path.
 */
public class CapturedWindow {

    public final UUID id;
    public final long hwnd;
    public final String title;

    private long sessionHandle;
    private int textureWidth;
    private int textureHeight;
    private final Identifier textureId;
    private DynamicTexture texture;
    private NativeImage pixels;

    private double worldX;
    private double worldY;
    private double worldZ;
    private float yawDegrees;
    private final int[] dimsScratch = new int[2];

    private InputMode inputMode = InputMode.WINDOWED;
    private boolean focused = false;

    public CapturedWindow(long hwnd, String title) {
        this.id = UUID.randomUUID();
        this.hwnd = hwnd;
        this.title = title;
        this.textureId = Identifier.fromNamespaceAndPath(Wincraft.MOD_ID, "capture/" + id);
    }

    /** Starts the native capture session. Returns false on failure. */
    public boolean start() {
        this.sessionHandle = WincraftNative.startCapture(hwnd);
        return sessionHandle != 0;
    }

    public void stop() {
        if (sessionHandle != 0) {
            WincraftNative.stopCapture(sessionHandle);
            sessionHandle = 0;
        }
        closeTexture();
    }

    /** Called once per client tick. Polls for a new captured frame. */
    public void update() {
        if (sessionHandle == 0) return;

        byte[] frame = WincraftNative.pollFrame(sessionHandle, dimsScratch);
        if (frame == null) return; // no new frame since last poll

        int width = dimsScratch[0];
        int height = dimsScratch[1];
        if (width <= 0 || height <= 0) return;

        uploadFrame(frame, width, height);
    }

    private void uploadFrame(byte[] frame, int width, int height) {
        if (frame.length < width * height * 4) {
            return;
        }

        if (width != textureWidth || height != textureHeight || texture == null || pixels == null) {
            closeTexture();
            textureWidth = width;
            textureHeight = height;
            pixels = new NativeImage(width, height, false);
            texture = new DynamicTexture(() -> "wincraft/" + title, pixels);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            Wincraft.LOGGER.info("Captured window {} frame size: {}x{}", title, width, height);
        }

        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int b = frame[i++] & 0xFF;
                int g = frame[i++] & 0xFF;
                int r = frame[i++] & 0xFF;
                int a = frame[i++] & 0xFF;
                pixels.setPixelABGR(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        texture.upload();
    }

    private void closeTexture() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            texture = null;
        } else if (pixels != null) {
            pixels.close();
        }
        pixels = null;
        textureWidth = 0;
        textureHeight = 0;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public boolean hasTexture() {
        return texture != null && textureWidth > 0 && textureHeight > 0;
    }

    public void setWorldPose(double x, double y, double z, float yawDegrees) {
        this.worldX = x;
        this.worldY = y;
        this.worldZ = z;
        this.yawDegrees = yawDegrees;
    }

    public double getWorldX() {
        return worldX;
    }

    public double getWorldY() {
        return worldY;
    }

    public double getWorldZ() {
        return worldZ;
    }

    public float getYawDegrees() {
        return yawDegrees;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setInputMode(InputMode mode) {
        this.inputMode = mode;
    }

    public void forwardMouseMove(int x, int y) {
        WincraftNative.mouseMove(hwnd, inputMode.nativeValue, x, y);
    }

    public void forwardMouseButton(int button, boolean down, int x, int y) {
        WincraftNative.mouseButton(hwnd, inputMode.nativeValue, button, down, x, y);
    }

    public void forwardMouseWheel(int delta, int x, int y) {
        WincraftNative.mouseWheel(hwnd, inputMode.nativeValue, delta, x, y);
    }

    public void forwardKey(int glfwKeyCode, boolean down) {
        int vk = VirtualKeyMap.toVirtualKey(glfwKeyCode);
        if (vk == -1) return;
        WincraftNative.keyEvent(hwnd, inputMode.nativeValue, vk, down);
    }

    public void forwardChar(int codepoint) {
        WincraftNative.charEvent(hwnd, codepoint);
    }
}
