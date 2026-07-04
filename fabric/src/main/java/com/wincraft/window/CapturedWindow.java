package com.wincraft.window;

import com.wincraft.natives.InputMode;
import com.wincraft.natives.VirtualKeyMap;
import com.wincraft.natives.WincraftNative;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Represents one live captured desktop window inside the Minecraft
 * world: owns the native capture session, uploads incoming frames as a
 * GPU texture, and forwards input while the window is "grabbed" or
 * "focused" — the equivalent of one Wayland client surface in the
 * original Linux mod.
 */
public class CapturedWindow {

    public final UUID id;
    public final long hwnd;
    public final String title;

    private long sessionHandle;
    private GpuTexture texture;
    private int textureWidth;
    private int textureHeight;
    private final int[] dimsScratch = new int[2];

    private InputMode inputMode = InputMode.WINDOWED;
    private boolean focused = false;

    public CapturedWindow(long hwnd, String title) {
        this.id = UUID.randomUUID();
        this.hwnd = hwnd;
        this.title = title;
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
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    /**
     * Called once per client tick / render frame. Polls for a new
     * captured frame and uploads it to the GPU texture if one arrived.
     * Mirrors how the Linux mod's compositor pushes committed Wayland
     * surface buffers onto the in-world quad texture.
     */
    public void update() {
        if (sessionHandle == 0) return;

        byte[] frame = WincraftNative.pollFrame(sessionHandle, dimsScratch);
        if (frame == null) return; // no new frame since last poll

        int width = dimsScratch[0];
        int height = dimsScratch[1];
        if (width <= 0 || height <= 0) return;

        uploadFrame(frame, width, height);
    }

    private void uploadFrame(byte[] bgra, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();

        if (texture == null || textureWidth != width || textureHeight != height) {
            if (texture != null) {
                texture.close();
            }
            texture = RenderSystem.getDevice().createTexture(
                    "wincraft/" + id,
                    TextureFormat.BGRA8,
                    width,
                    height,
                    1
            );
            textureWidth = width;
            textureHeight = height;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bgra);
        RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(texture, buffer, TextureFormat.BGRA8, 0, 0, 0, width, height);
    }

    public GpuTexture getTexture() {
        return texture;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
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

    // ---- Input forwarding — called from the input-capture handler
    // while this window is focused (mirrors waylandcraft's "hard
    // keyboard capture mode", default bind ALT-Q) ----

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
        if (vk == -1) return; // unmapped key, silently ignore
        WincraftNative.keyEvent(hwnd, inputMode.nativeValue, vk, down);
    }

    public void forwardChar(int codepoint) {
        WincraftNative.charEvent(hwnd, codepoint);
    }
}
