package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Minecraft;

/**
 * "Hard keyboard capture mode" — mirrors waylandcraft's ALT-Q behavior:
 * once toggled on while looking at a window, all subsequent mouse and
 * keyboard events are forwarded into the focused captured window
 * instead of controlling the player, until toggled off again.
 *
 * The actual input events are intercepted via Mixin into Minecraft's
 * mouse/keyboard handlers (see mixin/MouseMixin, KeyboardMixin) which
 * check {@link #isCapturing()} before letting events reach the game.
 */
public final class InputCaptureController {

    private static boolean capturing = false;

    private InputCaptureController() {}

    public static void toggle() {
        Minecraft client = Minecraft.getInstance();
        CapturedWindow focused = WindowManager.get().getFocused();

        if (capturing) {
            capturing = false;
            if (focused != null) focused.setFocused(false);
            client.mouse.lockCursor();
            return;
        }

        if (focused == null) {
            // Nothing focused (i.e. not looking at / holding a window) —
            // nothing to capture into.
            return;
        }

        capturing = true;
        focused.setFocused(true);
        client.mouse.unlockCursor();
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
