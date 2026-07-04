package com.wincraft.natives;

import com.wincraft.Wincraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JNI bridge to wincraft.dll (native/src/jni_bridge.rs).
 *
 * Method names and signatures here are the JNI contract; the Rust side
 * derives its exported symbol names from these exact package/class/method
 * names (Java_com_wincraft_native_WincraftNative_xxx). Keep them in sync.
 */
public final class WincraftNative {

    private static boolean loaded = false;
    private static Throwable loadError = null;

    private WincraftNative() {}

    /**
     * Extracts wincraft.dll from the mod jar into a temp file and loads
     * it. Safe to call multiple times; only loads once.
     */
    public static synchronized void ensureLoaded() {
        if (loaded || loadError != null) {
            return;
        }
        try {
            Path tempDir = Files.createTempDirectory("wincraft-native");
            Path dllPath = tempDir.resolve("wincraft.dll");

            try (InputStream in = WincraftNative.class.getResourceAsStream("/native/windows-x86_64/wincraft.dll")) {
                if (in == null) {
                    throw new IOException("wincraft.dll not found in mod resources — was the native build included?");
                }
                Files.copy(in, dllPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            System.load(dllPath.toAbsolutePath().toString());
            dllPath.toFile().deleteOnExit();

            loaded = ping();
            if (!loaded) {
                throw new IllegalStateException("wincraft.dll loaded but ping() returned false");
            }
            Wincraft.LOGGER.info("wincraft native library loaded successfully");
        } catch (Throwable t) {
            loadError = t;
            Wincraft.LOGGER.error("Failed to load wincraft native library", t);
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Throwable getLoadError() {
        return loadError;
    }

    // ---- Native methods, implemented in native/src/jni_bridge.rs ----

    /** Enumerates currently open, visible, capturable windows. */
    public static native WindowHandle[] enumerateWindows();

    /**
     * Starts capturing the given HWND via Windows.Graphics.Capture.
     * Returns an opaque session handle (0 = failure).
     */
    public static native long startCapture(long hwnd);

    /** Stops and releases a capture session. */
    public static native void stopCapture(long sessionHandle);

    /**
     * Polls for a new captured frame. Returns raw BGRA8 bytes (row-major,
     * top-down) or null if no new frame arrived since the last call.
     * dimsOut must be a pre-allocated int[2]; receives [width, height].
     */
    public static native byte[] pollFrame(long sessionHandle, int[] dimsOut);

    public static native void mouseMove(long hwnd, int mode, int x, int y);

    /** button: 0 = left, 1 = right */
    public static native void mouseButton(long hwnd, int mode, int button, boolean down, int x, int y);

    public static native void mouseWheel(long hwnd, int mode, int delta, int x, int y);

    public static native void keyEvent(long hwnd, int mode, int vkCode, boolean down);

    public static native void charEvent(long hwnd, int ch);

    /** Liveness check — returns true once the library is fully initialized. */
    public static native boolean ping();
}
