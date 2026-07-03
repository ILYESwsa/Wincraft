package com.wincraft.native;

/**
 * How input events get delivered to a captured window.
 *
 * WINDOWED (0): PostMessage-based, scoped to the window, doesn't steal
 * OS focus. Works for most classic Win32 apps. This is the default and
 * what should be used unless a specific app is known to need raw input.
 *
 * RAW_FOREGROUND (1): SendInput-based, system-wide, requires the
 * window to become the OS foreground window. Needed for apps that only
 * listen to raw input (some games, some Electron/Chromium apps).
 */
public enum InputMode {
    WINDOWED(0),
    RAW_FOREGROUND(1);

    public final int nativeValue;

    InputMode(int nativeValue) {
        this.nativeValue = nativeValue;
    }
}
