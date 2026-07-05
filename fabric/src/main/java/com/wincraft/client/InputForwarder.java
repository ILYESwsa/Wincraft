package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Central input bridge used by mixins on Minecraft's keyboard and mouse
 * handlers. When hard capture is active, events are forwarded to the
 * focused desktop window and cancelled before vanilla gameplay consumes them.
 *
 * Mouse position on the target window is derived from a crosshair
 * raycast (see WindowManager#raycastFocused), not raw screen coordinates
 * — since the crosshair is always screen-center in first person and
 * mouse-look only rotates the camera, "where the player is looking" is
 * the actual click target, not wherever GLFW's cursor happens to sit.
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
        WindowManager.HitResult hit = WindowManager.get().raycastFocused();
        if (hit == null) {
            // Looking away from the window entirely — still consume the
            // click so it doesn't leak into vanilla gameplay, just don't
            // forward it anywhere.
            return true;
        }
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
            window.forwardMouseButton(button.button(), action == GLFW.GLFW_PRESS, hit.textureX(), hit.textureY());
            return true;
        }
        return false;
    }

    public static boolean handleMouseMove(double x, double y) {
        CapturedWindow window = focusedCapture();
        if (window == null) {
            return false;
        }
        WindowManager.HitResult hit = WindowManager.get().raycastFocused();
        if (hit != null) {
            window.forwardMouseMove(hit.textureX(), hit.textureY());
        }
        return true;
    }

    public static boolean handleMouseScroll(double horizontal, double vertical) {
        CapturedWindow window = focusedCapture();
        if (window == null) {
            return false;
        }
        WindowManager.HitResult hit = WindowManager.get().raycastFocused();
        int delta = (int) Math.round(vertical * 120.0D);
        if (delta != 0 && hit != null) {
            window.forwardMouseWheel(delta, hit.textureX(), hit.textureY());
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
}
