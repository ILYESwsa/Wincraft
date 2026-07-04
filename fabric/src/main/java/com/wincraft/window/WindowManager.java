package com.wincraft.window;

import com.wincraft.natives.WincraftNative;
import com.wincraft.natives.WindowHandle;

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
        windows.put(window.id, window);
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
}
