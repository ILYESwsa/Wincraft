//! Enumerates top-level, visible, capturable windows on the desktop.
//! Feeds the in-game "app launcher" screen (bound to a key, mirroring
//! waylandcraft's launcher UX).

use windows::Win32::Foundation::{HWND, LPARAM, BOOL, TRUE};
use windows::Win32::UI::WindowsAndMessaging::{
    EnumWindows, GetWindowTextW, GetWindowTextLengthW, IsWindowVisible,
    GetWindowLongW, GWL_EXSTYLE, WS_EX_TOOLWINDOW, GetShellWindow, GetClassNameW,
};
use windows::Win32::UI::WindowsAndMessaging::IsIconic;

#[derive(Debug, Clone)]
pub struct WindowInfo {
    pub hwnd: isize,
    pub title: String,
    pub class_name: String,
}

/// Collects all windows that are reasonable candidates to show in the
/// picker: visible, have a title, not tool windows, not the desktop shell.
pub fn enumerate_windows() -> Vec<WindowInfo> {
    let mut results: Vec<WindowInfo> = Vec::new();
    let shell_hwnd = unsafe { GetShellWindow() };

    unsafe {
        let results_ptr = &mut results as *mut Vec<WindowInfo> as isize;
        let _ = EnumWindows(Some(enum_proc), LPARAM(results_ptr));
    }

    results.retain(|w| w.hwnd != shell_hwnd.0 as isize);
    results
}

unsafe extern "system" fn enum_proc(hwnd: HWND, lparam: LPARAM) -> BOOL {
    let results = &mut *(lparam.0 as *mut Vec<WindowInfo>);

    if !IsWindowVisible(hwnd).as_bool() {
        return TRUE;
    }
    if IsIconic(hwnd).as_bool() {
        // Minimized windows still capture in Windows.Graphics.Capture,
        // but we skip them from the picker list for clarity, same as
        // most alt-tab style UIs.
        return TRUE;
    }

    let ex_style = GetWindowLongW(hwnd, GWL_EXSTYLE) as u32;
    if (ex_style & WS_EX_TOOLWINDOW.0) != 0 {
        return TRUE;
    }

    let len = GetWindowTextLengthW(hwnd);
    if len == 0 {
        return TRUE;
    }

    let mut title_buf = vec![0u16; (len + 1) as usize];
    let copied = GetWindowTextW(hwnd, &mut title_buf);
    if copied == 0 {
        return TRUE;
    }
    let title = String::from_utf16_lossy(&title_buf[..copied as usize]);
    if title.trim().is_empty() {
        return TRUE;
    }

    let mut class_buf = [0u16; 256];
    let class_len = GetClassNameW(hwnd, &mut class_buf);
    let class_name = String::from_utf16_lossy(&class_buf[..class_len as usize]);

    // Filter out known non-app shell/system classes that pass the checks
    // above but aren't useful to show.
    const IGNORED_CLASSES: &[&str] = &[
        "Progman",
        "WorkerW",
        "Shell_TrayWnd",
        "Shell_SecondaryTrayWnd",
        "Windows.UI.Core.CoreWindow",
    ];
    if IGNORED_CLASSES.contains(&class_name.as_str()) {
        return TRUE;
    }

    results.push(WindowInfo {
        hwnd: hwnd.0 as isize,
        title,
        class_name,
    });

    TRUE
}
