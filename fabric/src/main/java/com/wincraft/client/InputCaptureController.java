package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;

/**
 * Hard-keyboard-capture-mode toggling (mirrors ALT-Q / "G" in the Linux
 * waylandcraft mod). While capturing:
 *  - KeyboardHandlerMixin redirects key/char events to the focused
 *    CapturedWindow instead of vanilla gameplay (see InputForwarder).
 *  - MouseHandlerMixin cancels button/scroll events from reaching vanilla
 *    gameplay, but movement is intentionally NOT cancelled and the mouse
 *    stays grabbed in normal camera-look mode. The crosshair (screen
 *    center) is the "cursor": InputForwarder resolves where you're
 *    pointing by raycasting the player's look direction against the
 *    window's plane (see WindowManager#raycastFocused), not by tracking
 *    a separate free-floating 2D pointer. This avoids ever needing an
 *    invisible/untracked OS cursor released onto the desktop, which
 *    previously left clicks with nothing visible to aim with.
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
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void release() {
        if (!capturing) return;
        capturing = false;

        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused != null) focused.setFocused(false);
    }
}
