package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Central input bridge used by mixins on Minecraft's keyboard and mouse
 * handlers. When hard capture is active, events are forwarded to the
 * focused desktop window and cancelled before vanilla gameplay consumes them.
 */
public final class InputForwarder {

    private InputForwarder() {}

    public static boolean handleKey(KeyEvent event, int action) {
        CapturedWindow window = focusedCapture();
        if (window == null) {
            return false;
        }

        if (event.key() == GLFW.GLFW_KEY_Q && (event.modifiers() & GLFW.GLFW_MOD_ALT) != 0 && action == GLFW.GLFW_PRESS) {
            InputCaptureController.release();
            return true;
        }

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            window.forwardKey(event.key(), true);
            return true;
        }
        if (action == GLFW.GLFW_RELEASE) {
            window.forwardKey(event.key(), false);
            return true;
        }
        return false;
    }

    public static boolean handleChar(CharacterEvent event) {
        CapturedWindow window = focusedCapture();
        if (window == null) {
            return false;
        }
        window.forwardChar(event.codepoint());
        return true;
    }

    public static boolean handleMouseButton(MouseButtonInfo button, int action) {
        CapturedWindow window = focusedCapture();
        if (window == null) {
            return false;
        }
        int[] coords = currentWindowCoords(window);
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
            window.forwardMouseButton(button.button(), action == GLFW.GLFW_PRESS, coords[0], coords[1]);
            return true;
        }
        return false;
    }

    public static boolean handleMouseMove(double x, double y) {
        CapturedWindow window = focusedCapture();
        if (window == null) {
            return false;
        }
        int[] coords = screenToWindowCoords(window, x, y);
        window.forwardMouseMove(coords[0], coords[1]);
        return true;
    }

    public static boolean handleMouseScroll(double horizontal, double vertical) {
        CapturedWindow window = focusedCapture();
        if (window == null) {
            return false;
        }
        int[] coords = currentWindowCoords(window);
        int delta = (int) Math.round(vertical * 120.0D);
        if (delta != 0) {
            window.forwardMouseWheel(delta, coords[0], coords[1]);
        }
        return true;
    }

    private static CapturedWindow focusedCapture() {
        if (!InputCaptureController.isCapturing()) {
            return null;
        }
        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused == null || focused.getTextureWidth() <= 0 || focused.getTextureHeight() <= 0) {
            return null;
        }
        return focused;
    }

    private static int[] currentWindowCoords(CapturedWindow window) {
        Minecraft client = Minecraft.getInstance();
        return screenToWindowCoords(window, client.mouseHandler.xpos(), client.mouseHandler.ypos());
    }

    private static int[] screenToWindowCoords(CapturedWindow window, double x, double y) {
        Minecraft client = Minecraft.getInstance();
        double width = Math.max(1, client.getWindow().getWidth());
        double height = Math.max(1, client.getWindow().getHeight());
        int targetX = clamp((int) Math.round(x / width * window.getTextureWidth()), 0, window.getTextureWidth() - 1);
        int targetY = clamp((int) Math.round(y / height * window.getTextureHeight()), 0, window.getTextureHeight() - 1);
        return new int[] { targetX, targetY };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
