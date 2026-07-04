package com.wincraft.window;

import com.wincraft.Wincraft;
import com.wincraft.natives.InputMode;
import com.wincraft.natives.VirtualKeyMap;
import com.wincraft.natives.WincraftNative;

import java.util.UUID;

/**
 * Represents one live captured desktop window: owns the native capture
 * session and forwards input while the window is "focused".
 *
 * NOTE: GPU texture upload is stubbed out for now (logs frame arrival
 * only) pending confirmation of Blaze3D's exact texture API surface in
 * MC 26.1 — RenderSystem/GpuTexture/TextureFormat names used in earlier
 * drafts were unverified guesses and didn't compile. Once the native
 * capture -> JNI -> Java frame pipeline is confirmed working end to end,
 * this is the only piece that needs re-adding.
 */
public class CapturedWindow {

    public final UUID id;
    public final long hwnd;
    public final String title;

    private long sessionHandle;
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
    }

    /** Called once per client tick. Polls for a new captured frame. */
    public void update() {
        if (sessionHandle == 0) return;

        byte[] frame = WincraftNative.pollFrame(sessionHandle, dimsScratch);
        if (frame == null) return; // no new frame since last poll

        int width = dimsScratch[0];
        int height = dimsScratch[1];
        if (width <= 0 || height <= 0) return;

        if (width != textureWidth || height != textureHeight) {
            textureWidth = width;
            textureHeight = height;
            Wincraft.LOGGER.info("Captured window {} frame size: {}x{}", title, width, height);
        }
        // TODO: upload `frame` (raw BGRA8 bytes) to a GPU texture once
        // the correct Blaze3D API for MC 26.1 is confirmed.
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
