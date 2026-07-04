package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Hard-keyboard-capture-mode toggling (mirrors ALT-Q / "G" in the Linux
 * waylandcraft mod). While capturing:
 *  - MouseHandlerMixin/KeyboardHandlerMixin redirect input to the focused
 *    CapturedWindow instead of vanilla gameplay (see InputForwarder).
 *  - The OS cursor is switched from GLFW's disabled/raw-look mode to
 *    normal mode so it becomes a visible, absolute-position pointer the
 *    player can see moving over the in-world window texture, instead of
 *    an invisible relative-delta look-cursor.
 *
 * Releasing restores vanilla's disabled cursor mode so camera look
 * resumes exactly as before capture started.
 */
public final class InputCaptureController {

    private static boolean capturing = false;

    private InputCaptureController() {}

    public static void toggle() {
        if (capturing) {
            release();
            return;
        }

        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused == null) return;

        capturing = true;
        focused.setFocused(true);
        setCursorNormal();
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void release() {
        if (!capturing) return;
        capturing = false;

        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused != null) focused.setFocused(false);

        restoreCursorGrab();
    }

    private static void setCursorNormal() {
        long handle = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    private static void restoreCursorGrab() {
        Minecraft client = Minecraft.getInstance();
        // Only re-grab if the game still owns input (e.g. no pause/other
        // screen got opened while we were capturing) — mouseHandler.grabMouse()
        // is the same call vanilla uses when closing a Screen back to gameplay.
        if (client.screen == null) {
            client.mouseHandler.grabMouse();
        } else {
            long handle = client.getWindow().getWindow();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }
}
