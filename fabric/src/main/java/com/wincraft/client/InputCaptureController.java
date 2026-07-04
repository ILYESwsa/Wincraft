package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Minecraft;

/**
 * Hard-keyboard-capture-mode toggling (mirrors ALT-Q / "G" in the Linux
 * waylandcraft mod). While capturing:
 *  - MouseHandlerMixin/KeyboardHandlerMixin redirect input to the focused
 *    CapturedWindow instead of vanilla gameplay (see InputForwarder).
 *  - The OS cursor is released via vanilla's own MouseHandler#releaseMouse
 *    (the same call the pause menu uses) so it becomes a normal, visible
 *    pointer instead of GLFW's disabled/raw-look camera cursor.
 *
 * Releasing re-grabs the mouse via MouseHandler#grabMouse so camera look
 * resumes exactly as before capture started. This deliberately avoids
 * touching the raw GLFW window handle directly — 26.1's Window class
 * restructured around a GpuBackend and no longer exposes that the same
 * way older versions did, so going through MouseHandler keeps this code
 * on stable, documented vanilla API instead of a native pointer we'd
 * have to keep re-guessing the accessor for.
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
        Minecraft.getInstance().mouseHandler.releaseMouse();
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void release() {
        if (!capturing) return;
        capturing = false;

        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused != null) focused.setFocused(false);

        Minecraft client = Minecraft.getInstance();
        // Only re-grab if the game still owns input (e.g. no pause/other
        // screen got opened while we were capturing) — grabbing while a
        // Screen is open would fight the screen for cursor visibility.
        if (client.screen == null) {
            client.mouseHandler.grabMouse();
        }
    }
}
