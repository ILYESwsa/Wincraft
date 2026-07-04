//! Forwards captured Minecraft input (mouse + keyboard, while a window
//! is "focused" in-world) into the target HWND.
//!
//! Two delivery paths are exposed because different apps expect
//! different input styles:
//!   - `PostMessage`-based: works for most classic Win32 apps, is
//!     window-scoped (doesn't require the window to be OS-focused).
//!   - `SendInput`-based: works for apps that only listen to raw input
//!     (some games / Electron / Chromium apps), but requires the
//!     window to actually be foreground.

use windows::Win32::Foundation::{HWND, WPARAM, LPARAM, POINT};
use windows::Win32::Graphics::Gdi::{ClientToScreen};
use windows::Win32::UI::WindowsAndMessaging::{
    PostMessageW, SetForegroundWindow,
    WM_LBUTTONDOWN, WM_LBUTTONUP, WM_RBUTTONDOWN, WM_RBUTTONUP,
    WM_MOUSEMOVE, WM_MOUSEWHEEL, WM_KEYDOWN, WM_KEYUP, WM_CHAR,
};
use windows::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_MOUSE, INPUT_KEYBOARD, MOUSEINPUT, KEYBDINPUT,
    MOUSEEVENTF_MOVE, MOUSEEVENTF_ABSOLUTE, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP,
    MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_WHEEL,
    KEYEVENTF_KEYUP, KEYBD_EVENT_FLAGS, VIRTUAL_KEY,
};

/// windows-rs doesn't export the classic MAKELPARAM C macro — it's just
/// bit-packing two u16s into the low/high words of an isize-sized LPARAM.
#[inline]
fn make_lparam(low: u16, high: u16) -> LPARAM {
    LPARAM(((high as isize) << 16) | (low as isize & 0xFFFF))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InputMode {
    /// PostMessage to the window, no foreground/focus stealing.
    Windowed,
    /// SendInput to the whole system; window must be foreground.
    RawForeground,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MouseButton {
    Left,
    Right,
}

/// Move the mouse cursor over the target window at window-relative
/// coordinates (x, y) in pixels.
pub fn mouse_move(hwnd: isize, mode: InputMode, x: i32, y: i32) {
    let hwnd = HWND(hwnd as *mut _);
    match mode {
        InputMode::Windowed => unsafe {
            let lparam = make_lparam(x as u16, y as u16);
            let _ = PostMessageW(hwnd, WM_MOUSEMOVE, WPARAM(0), lparam);
        },
        InputMode::RawForeground => unsafe {
            let mut pt = POINT { x, y };
            let _ = ClientToScreen(hwnd, &mut pt);
            send_absolute_mouse_move(pt.x, pt.y);
        },
    }
}

pub fn mouse_button(hwnd: isize, mode: InputMode, button: MouseButton, down: bool, x: i32, y: i32) {
    let hwnd = HWND(hwnd as *mut _);
    match mode {
        InputMode::Windowed => unsafe {
            let lparam = make_lparam(x as u16, y as u16);
            let msg = match (button, down) {
                (MouseButton::Left, true) => WM_LBUTTONDOWN,
                (MouseButton::Left, false) => WM_LBUTTONUP,
                (MouseButton::Right, true) => WM_RBUTTONDOWN,
                (MouseButton::Right, false) => WM_RBUTTONUP,
            };
            let _ = PostMessageW(hwnd, msg, WPARAM(0), lparam);
        },
        InputMode::RawForeground => unsafe {
            let _ = SetForegroundWindow(hwnd);
            let flags = match (button, down) {
                (MouseButton::Left, true) => MOUSEEVENTF_LEFTDOWN,
                (MouseButton::Left, false) => MOUSEEVENTF_LEFTUP,
                (MouseButton::Right, true) => MOUSEEVENTF_RIGHTDOWN,
                (MouseButton::Right, false) => MOUSEEVENTF_RIGHTUP,
            };
            send_mouse_event(flags, 0, 0, 0);
        },
    }
}

pub fn mouse_wheel(hwnd: isize, mode: InputMode, delta: i32, x: i32, y: i32) {
    let hwnd = HWND(hwnd as *mut _);
    match mode {
        InputMode::Windowed => unsafe {
            let lparam = make_lparam(x as u16, y as u16);
            let wparam = WPARAM(((delta as u32) << 16) as usize);
            let _ = PostMessageW(hwnd, WM_MOUSEWHEEL, wparam, lparam);
        },
        InputMode::RawForeground => unsafe {
            let _ = SetForegroundWindow(hwnd);
            send_mouse_event(MOUSEEVENTF_WHEEL, 0, 0, delta as i32);
        },
    }
}

pub fn key_event(hwnd: isize, mode: InputMode, vk_code: u16, down: bool) {
    let hwnd = HWND(hwnd as *mut _);
    match mode {
        InputMode::Windowed => unsafe {
            let msg = if down { WM_KEYDOWN } else { WM_KEYUP };
            let _ = PostMessageW(hwnd, msg, WPARAM(vk_code as usize), LPARAM(0));
        },
        InputMode::RawForeground => unsafe {
            let _ = SetForegroundWindow(hwnd);
            send_key_event(vk_code, down);
        },
    }
}

/// Sends a text character directly (used for typed text where Java has
/// already resolved the Unicode codepoint via its own IME/layout
/// handling — avoids needing xkbcommon-style layout logic natively).
pub fn char_event(hwnd: isize, ch: u16) {
    unsafe {
        let hwnd = HWND(hwnd as *mut _);
        let _ = PostMessageW(hwnd, WM_CHAR, WPARAM(ch as usize), LPARAM(0));
    }
}

unsafe fn send_mouse_event(flags: windows::Win32::UI::Input::KeyboardAndMouse::MOUSE_EVENT_FLAGS, dx: i32, dy: i32, wheel: i32) {
    let input = INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: windows::Win32::UI::Input::KeyboardAndMouse::INPUT_0 {
            mi: MOUSEINPUT {
                dx,
                dy,
                mouseData: wheel as u32,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
}

unsafe fn send_absolute_mouse_move(screen_x: i32, screen_y: i32) {
    let screen_w = windows::Win32::UI::WindowsAndMessaging::GetSystemMetrics(
        windows::Win32::UI::WindowsAndMessaging::SM_CXSCREEN,
    );
    let screen_h = windows::Win32::UI::WindowsAndMessaging::GetSystemMetrics(
        windows::Win32::UI::WindowsAndMessaging::SM_CYSCREEN,
    );
    let norm_x = (screen_x as f32 / screen_w as f32 * 65535.0) as i32;
    let norm_y = (screen_y as f32 / screen_h as f32 * 65535.0) as i32;
    send_mouse_event(MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE, norm_x, norm_y, 0);
}

unsafe fn send_key_event(vk_code: u16, down: bool) {
    let flags: KEYBD_EVENT_FLAGS = if down {
        KEYBD_EVENT_FLAGS(0)
    } else {
        KEYEVENTF_KEYUP
    };
    let input = INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: windows::Win32::UI::Input::KeyboardAndMouse::INPUT_0 {
            ki: KEYBDINPUT {
                wVk: VIRTUAL_KEY(vk_code),
                wScan: 0,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
}
