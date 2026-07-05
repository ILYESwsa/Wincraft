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

    /**
     * Result of a crosshair raycast against a window's quad: which
     * window was hit, and where on its texture (0,0 = top-left,
     * width-1,height-1 = bottom-right) the crosshair lands.
     */
    public record HitResult(CapturedWindow window, int textureX, int textureY) {}

    /**
     * Casts a ray from the player's eye along their look direction and
     * finds where it crosses the focused window's plane, converting that
     * intersection into exact texture-pixel coordinates.
     *
     * Since the crosshair is always screen-center in first person, and
     * mouse-look only ever rotates the camera (never moves a separate 2D
     * cursor), "where the player is looking" is exactly the same ray
     * InputForwarder needs for click accuracy — no separate
     * screen-to-world unprojection math is needed.
     */
    public HitResult raycastFocused() {
        CapturedWindow window = getFocused();
        if (window == null) return null;
        return raycast(window);
    }

    private HitResult raycast(CapturedWindow window) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // Plane normal: the quad faces the same direction it was placed
        // to face (see WincraftWorldRenderer's yaw rotation), i.e.
        // "180 - yaw" degrees around Y, matching the renderer exactly.
        double yawRad = Math.toRadians(window.getYawDegrees());
        double normalX = -Math.sin(yawRad);
        double normalZ = Math.cos(yawRad);

        Vec3 planePoint = new Vec3(window.getWorldX(), window.getWorldY(), window.getWorldZ());
        Vec3 planeNormal = new Vec3(normalX, 0.0D, normalZ);

        double denom = look.dot(planeNormal);
        if (Math.abs(denom) < 1.0E-6D) return null; // looking parallel to the plane

        double t = planePoint.subtract(eye).dot(planeNormal) / denom;
        if (t <= 0.0D) return null; // plane is behind the player

        Vec3 hit = eye.add(look.scale(t));
        Vec3 local = hit.subtract(planePoint);

        // Project the hit point onto the plane's local right/up axes.
        // "Right" is perpendicular to the normal in the XZ plane; "up" is
        // world Y, matching the renderer's unrotated vertical axis.
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double localRight = local.x * rightX + local.z * rightZ;
        double localUp = local.y;

        float aspect = window.getTextureHeight() == 0
                ? 1.0F
                : (float) window.getTextureWidth() / (float) window.getTextureHeight();
        float halfWidth = window.getWidthBlocks() * 0.5F;
        float halfHeight = halfWidth / Math.max(0.1F, aspect);

        if (Math.abs(localRight) > halfWidth || Math.abs(localUp) > halfHeight) {
            return null; // hit the plane, but outside the actual quad bounds
        }

        // Renderer's front-face UVs: (-halfWidth, halfHeight) -> (0,0),
        // (halfWidth, -halfHeight) -> (1,1). Match that mapping exactly.
        double u = (localRight + halfWidth) / (2.0D * halfWidth);
        double v = (halfHeight - localUp) / (2.0D * halfHeight);

        int textureX = (int) Math.round(u * (window.getTextureWidth() - 1));
        int textureY = (int) Math.round(v * (window.getTextureHeight() - 1));
        textureX = Math.max(0, Math.min(window.getTextureWidth() - 1, textureX));
        textureY = Math.max(0, Math.min(window.getTextureHeight() - 1, textureY));

        return new HitResult(window, textureX, textureY);
    }
}
