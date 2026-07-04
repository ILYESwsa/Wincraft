package com.wincraft.window;

import com.wincraft.natives.WincraftNative;
import com.wincraft.natives.WindowHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central registry of all captured windows currently open in the
 * client session. Equivalent role to the compositor's client-surface
 * table in the Linux mod, minus the actual compositing (Windows' DWM
 * already composited these frames for us before capture).
 */
public final class WindowManager {

    private static final WindowManager INSTANCE = new WindowManager();

    private final Map<UUID, CapturedWindow> windows = new LinkedHashMap<>();
    private UUID focusedWindow = null;

    private WindowManager() {}

    public static WindowManager get() {
        return INSTANCE;
    }

    /** Lists windows available to open, for the app-launcher screen. */
    public List<WindowHandle> listAvailableWindows() {
        if (!WincraftNative.isLoaded()) {
            return List.of();
        }
        WindowHandle[] handles = WincraftNative.enumerateWindows();
        return handles == null ? List.of() : List.of(handles);
    }

    /** Opens a new captured window from a picked WindowHandle. */
    public CapturedWindow open(WindowHandle handle) {
        CapturedWindow window = new CapturedWindow(handle.hwnd, handle.title);
        if (!window.start()) {
            return null;
        }
        placeInFrontOfPlayer(window);
        windows.put(window.id, window);
        setFocused(window.id);
        return window;
    }

    public void close(UUID id) {
        CapturedWindow window = windows.remove(id);
        if (window != null) {
            window.stop();
        }
        if (id.equals(focusedWindow)) {
            focusedWindow = null;
        }
    }

    public void closeAll() {
        for (CapturedWindow window : new ArrayList<>(windows.values())) {
            window.stop();
        }
        windows.clear();
        focusedWindow = null;
    }

    public Collection<CapturedWindow> all() {
        return windows.values();
    }

    public CapturedWindow get(UUID id) {
        return windows.get(id);
    }

    /** Called every client tick to pull fresh frames for all open windows. */
    public void tick() {
        for (CapturedWindow window : windows.values()) {
            window.update();
        }
    }

    // ---- Focus (hard keyboard capture mode, mirrors ALT-Q in the
    // Linux mod) ----

    public void setFocused(UUID id) {
        if (focusedWindow != null) {
            CapturedWindow prev = windows.get(focusedWindow);
            if (prev != null) prev.setFocused(false);
        }
        focusedWindow = id;
        CapturedWindow next = windows.get(id);
        if (next != null) next.setFocused(true);
    }

    public void clearFocus() {
        if (focusedWindow != null) {
            CapturedWindow prev = windows.get(focusedWindow);
            if (prev != null) prev.setFocused(false);
        }
        focusedWindow = null;
    }

    public CapturedWindow getFocused() {
        return focusedWindow == null ? null : windows.get(focusedWindow);
    }

    public boolean hasFocus() {
        return focusedWindow != null;
    }

    private void placeInFrontOfPlayer(CapturedWindow window) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            window.setWorldPose(0.0D, 80.0D, 0.0D, 0.0F);
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 pos = eye.add(look.scale(3.0D));
        window.setWorldPose(pos.x, pos.y, pos.z, player.getYRot());
    }

    // ---- Move / resize (used by WindowManagerScreen, mirrors the Linux
    // mod's "grab window" interactions) ----

    /** Re-places a window a given distance in front of the player, facing them. */
    public void moveInFrontOfPlayer(CapturedWindow window, double distanceBlocks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 pos = eye.add(look.scale(distanceBlocks));
        window.setWorldPose(pos.x, pos.y, pos.z, player.getYRot());
    }

    /** Finds the window whose center the player is most directly looking at, within a cone. */
    public CapturedWindow findLookedAt(double maxDistance) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        CapturedWindow best = null;
        double bestDot = 0.90D; // ~25 degree half-angle cone
        for (CapturedWindow window : windows.values()) {
            Vec3 toWindow = new Vec3(
                    window.getWorldX() - eye.x,
                    window.getWorldY() - eye.y,
                    window.getWorldZ() - eye.z
            );
            double distance = toWindow.length();
            if (distance < 0.001D || distance > maxDistance) continue;

            double dot = toWindow.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = window;
            }
        }
        return best;
    }
}
