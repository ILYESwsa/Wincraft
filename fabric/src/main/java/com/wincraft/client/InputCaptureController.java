package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;

/**
 * Placeholder for hard-keyboard-capture-mode toggling. Cursor lock/unlock
 * calls removed temporarily pending confirmation of the correct
 * Minecraft field/method names in 26.1 — focus state tracking still works,
 * just doesn't yet grab the OS cursor.
 */
public final class InputCaptureController {

    private static boolean capturing = false;

    private InputCaptureController() {}

    public static void toggle() {
        CapturedWindow focused = WindowManager.get().getFocused();

        if (capturing) {
            capturing = false;
            if (focused != null) focused.setFocused(false);
            return;
        }

        if (focused == null) return;

        capturing = true;
        focused.setFocused(true);
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void release() {
        capturing = false;
        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused != null) focused.setFocused(false);
    }
}
