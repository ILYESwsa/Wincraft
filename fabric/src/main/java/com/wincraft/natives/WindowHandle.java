package com.wincraft.natives;

/**
 * Describes one capturable desktop window, as returned by
 * {@link WincraftNative#enumerateWindows()}.
 *
 * Constructed directly from native code via JNI — the constructor
 * signature (J Ljava/lang/String; Ljava/lang/String;) is referenced by
 * exact string in native/src/jni_bridge.rs, so don't change it without
 * updating the Rust side too.
 */
public final class WindowHandle {
    public final long hwnd;
    public final String title;
    public final String className;

    public WindowHandle(long hwnd, String title, String className) {
        this.hwnd = hwnd;
        this.title = title;
        this.className = className;
    }

    @Override
    public String toString() {
        return title + " (" + className + ")";
    }
}
