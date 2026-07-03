# Wincraft

Capture and control Windows desktop applications as grabbable windows inside Minecraft — a Windows-targeted counterpart to [waylandcraft](https://github.com/EVV1E/waylandcraft), which does the same thing on Linux via a real Wayland compositor.

**This is not a Wayland port.** Wayland cannot run outside Linux. Instead of implementing a display protocol, Wincraft captures existing Windows application windows using the OS's own compositor (via `Windows.Graphics.Capture`) and replays input into them — same end-user feature, different plumbing underneath.

## How it works

| Layer | Tech |
|---|---|
| In-world rendering, grab/move/snap UX, settings, app launcher | Java, Fabric mod (`fabric/`) |
| Window enumeration, frame capture, input injection | Rust `cdylib` → `wincraft.dll` (`native/`), using [`windows-rs`](https://github.com/microsoft/windows-rs) |
| Bridge between the two | JNI (`com.wincraft.native.WincraftNative`) |

Frames are captured via `Windows.Graphics.Capture` (the same API OBS and Xbox Game Bar use), copied to a CPU-readable buffer, and uploaded as a GPU texture onto an in-world quad each tick. Input is forwarded either via `PostMessage` (window-scoped, doesn't steal OS focus — default) or `SendInput` (system-wide, for apps that need raw input and can tolerate stealing foreground focus).

## Requirements

- **Windows 10 (2004+) or Windows 11** — `Windows.Graphics.Capture` is unavailable on older builds.
- Minecraft 26.1.2, Fabric Loader ≥0.18, Fabric API.
- To build: Rust toolchain (`x86_64-pc-windows-msvc` target) + Java 25 SDK.

## Building

CI (GitHub Actions, `.github/workflows/build.yml`) does this automatically on every push — it builds the native DLL on a `windows-latest` runner, then packages it into the mod jar. To build locally on a Windows machine:

```powershell
# 1. Build the native DLL
pwsh native/build.ps1

# 2. Build the mod jar (bundles the DLL from native/target/release/)
.\gradlew.bat :fabric:build
```

Output jar: `fabric/build/libs/wincraft-<version>.jar`

## Default controls

Mirrors waylandcraft's bindings where the concept carries over:

- **B** — open the window launcher (lists currently open Windows apps)
- **Alt+Q** (while looking at / holding a window) — toggle hard input capture: mouse and keyboard get forwarded into the window instead of controlling the player
- Grab/move/scroll/snap behavior on captured windows is unchanged from the base Fabric input handling

## Known limitations

- **Fullscreen exclusive apps** (some games) often can't be captured cleanly by `Windows.Graphics.Capture` — works best on windowed/borderless-windowed apps.
- **Anti-cheat-protected applications** may refuse capture entirely or flag `SendInput`-based input injection. Don't point this at anti-cheat games.
- `PostMessage`-based input doesn't reach apps that only listen for raw system input (some Electron/Chromium apps, some games) — those need `RAW_FOREGROUND` mode, which steals OS focus while active.
- No audio capture/forwarding yet (waylandcraft's Linux side gets this via PulseAudio; there's no equivalent wired up here).
- Multiplayer window-sharing is scaffolded (`Wincraft.onInitialize`) but not implemented — client-side only for now, same starting point as waylandcraft's first release.

## License

GPL-3.0, matching the project this is inspired by. See `LICENSE`.
