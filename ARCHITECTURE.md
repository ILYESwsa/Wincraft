# Wincraft architecture

Wincraft is split into a small Fabric client mod and a Windows-only native DLL.
The Java side owns Minecraft integration and lifecycle; the Rust side owns HWND
enumeration, Windows Graphics Capture, and Windows input injection.

## Runtime layers

```text
Minecraft client
  |
  | Fabric entrypoints
  v
fabric/src/main/java/com/wincraft/client/WincraftClient.java
  |  - loads the native DLL
  |  - registers the B key launcher shortcut
  |  - ticks the WindowManager once per client tick
  v
fabric/src/main/java/com/wincraft/client/gui/WindowLauncherScreen.java
  |  - asks WindowManager for capturable HWNDs
  |  - lets the user select one
  v
fabric/src/main/java/com/wincraft/window/WindowManager.java
  |  - tracks CapturedWindow instances by UUID
  |  - manages focused/captured window state
  |  - closes sessions on client shutdown
  v
fabric/src/main/java/com/wincraft/window/CapturedWindow.java
  |  - owns one opaque native capture-session handle
  |  - polls BGRA8 frames each tick
  |  - forwards input events through JNI
  v
fabric/src/main/java/com/wincraft/natives/WincraftNative.java
  |  - extracts/loads wincraft.dll
  |  - declares the JNI contract
  v
native/src/jni_bridge.rs
  |  - converts Java calls and Java arrays/objects to Rust values
  v
native/src/{enumerate,capture,input,session}.rs
     - EnumWindows candidate listing
     - Windows.Graphics.Capture frame acquisition
     - PostMessage/SendInput forwarding
     - opaque handle table for capture sessions
```

## Window-open lifecycle

1. `WincraftClient` calls `WincraftNative.ensureLoaded()` during client initialization.
2. The same initializer registers the `B` keybinding and a client tick callback.
3. On tick, `WindowManager.tick()` polls every live captured window.
4. When the user presses `B`, `WindowLauncherScreen` opens.
5. `WindowLauncherScreen.init()` calls `WindowManager.listAvailableWindows()`.
6. `WindowManager` calls `WincraftNative.enumerateWindows()`.
7. `native/src/jni_bridge.rs` calls `enumerate::enumerate_windows()` and returns a Java `WindowHandle[]`.
8. Clicking a button calls `WindowManager.open(handle)`.
9. `CapturedWindow.start()` calls `WincraftNative.startCapture(hwnd)`.
10. `native/src/jni_bridge.rs` starts `CaptureSession` and stores it in `session.rs`, returning an opaque integer handle to Java.
11. Each client tick, `CapturedWindow.update()` calls `WincraftNative.pollFrame(sessionHandle, dimsOut)`.
12. Rust returns the newest BGRA8 frame, if one has arrived, and writes width/height into `dimsOut`.
13. Java uploads the BGRA8 frame into a `DynamicTexture` and the level renderer draws it on an in-world quad.

## JNI contract

`WincraftNative` and `native/src/jni_bridge.rs` must stay in lockstep. Java method
names, packages, parameter types, and return types directly determine the exported
JNI symbol names, for example:

```text
com.wincraft.natives.WincraftNative.startCapture(long)
  -> Java_com_wincraft_natives_WincraftNative_startCapture(JNIEnv, JClass, jlong)
```

When changing either side, update the other side in the same patch and run both a
Java build and a Windows native build.

## Input modes

Wincraft exposes two native input paths:

- `WINDOWED` uses `PostMessageW` against the target HWND. It is scoped to the
  target window and does not intentionally steal OS focus.
- `RAW_FOREGROUND` uses `SendInput`. It can reach applications that ignore window
  messages, but it may foreground the target application and sends system-wide input.

Prefer `WINDOWED` unless a target application is known to require raw/system input.
Avoid anti-cheat-protected applications.

## Known implementation gaps

- Captured frames are uploaded to Java `DynamicTexture` instances and rendered on
  simple in-world quads; this should still be optimized to avoid per-pixel Java
  copies for large windows.
- `InputCaptureController` tracks hard-capture state and mixins forward raw
  Minecraft keyboard/mouse callbacks, but cursor-lock and precise hit-tested
  window-local coordinates still need refinement.
- Window movement, resizing, snapping, and in-world hit-testing are not implemented.
- Multiplayer window sharing is only scaffolded in the main initializer.
- Native capture and input behavior must be validated on Windows; most Linux CI
  checks can only compile/package the Java side after a Windows job supplies the DLL.

## Recommended next milestones

1. Replace the current per-pixel BGRA-to-ABGR Java copy path with a faster direct
   upload path once the best Minecraft 26.1 API is confirmed.
2. Add in-world hit-testing so mouse coordinates map to the actual quad instead
   of the current screen-relative approximation.
3. Add grab/move/resize/snap controls for captured quads.
4. Add a visible focus selector and bind Alt+Q to `InputCaptureController.toggle()`.
5. Add better native error reporting for failed capture starts and failed frame polls.
6. Add Windows integration testing instructions for native capture and input behavior.
