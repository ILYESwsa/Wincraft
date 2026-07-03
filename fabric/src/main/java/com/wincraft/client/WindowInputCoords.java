package com.wincraft.client;

import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;

/**
 * While a captured window is focused, Minecraft's cursor is locked
 * (FPS-style raw delta mouse look, since the player is still "in the
 * world" looking at a 3D quad). We can't directly read an absolute
 * screen position the way a normal desktop app would — so this class
 * accumulates raw deltas into a virtual cursor position clamped to the
 * focused window's captured pixel dimensions.
 *
 * This is the equivalent of waylandcraft's screen-space-to-surface-space
 * projection math (raycasting the in-world quad), simplified here to a
 * delta-accumulation model since we don't yet raycast the actual 3D
 * quad geometry — see WindowRenderer for where that hookup belongs
 * once the in-world block/entity rendering is implemented.
 */
public final class WindowInputCoords {

    private static double virtualX = 0;
    private static double virtualY = 0;
    private static double lastRawX = Double.NaN;
    private static double lastRawY = Double.NaN;

    private WindowInputCoords() {}

    /** Call when a window becomes focused, to re-center the virtual cursor. */
    public static void resetToCenter() {
        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused == null) return;
        virtualX = focused.getTextureWidth() / 2.0;
        virtualY = focused.getTextureHeight() / 2.0;
        lastRawX = Double.NaN;
        lastRawY = Double.NaN;
    }

    /**
     * Accumulates a raw screen-space cursor sample into the virtual
     * cursor, clamped to the focused window's pixel bounds. Returns
     * [x, y] as ints suitable for native input calls.
     */
    public static int[] updateFromScreenDelta(double rawX, double rawY) {
        CapturedWindow focused = WindowManager.get().getFocused();
        if (focused == null) return new int[]{0, 0};

        if (Double.isNaN(lastRawX)) {
            lastRawX = rawX;
            lastRawY = rawY;
        }

        double dx = rawX - lastRawX;
        double dy = rawY - lastRawY;
        lastRawX = rawX;
        lastRawY = rawY;

        int w = Math.max(1, focused.getTextureWidth());
        int h = Math.max(1, focused.getTextureHeight());

        virtualX = clamp(virtualX + dx, 0, w - 1);
        virtualY = clamp(virtualY + dy, 0, h - 1);

        return new int[]{(int) virtualX, (int) virtualY};
    }

    public static int[] lastRelative() {
        return new int[]{(int) virtualX, (int) virtualY};
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
