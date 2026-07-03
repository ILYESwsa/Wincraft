//! Java only ever holds an opaque i64 "handle" for a capture session.
//! This module owns the actual `CaptureSession` objects and maps
//! handles to them, since JNI can't pass Rust references across the
//! boundary directly.

use std::collections::HashMap;
use once_cell::sync::Lazy;
use parking_lot::Mutex;

use crate::capture::CaptureSession;

static SESSIONS: Lazy<Mutex<HashMap<i64, CaptureSession>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));
static NEXT_HANDLE: Lazy<Mutex<i64>> = Lazy::new(|| Mutex::new(1));

pub fn register(session: CaptureSession) -> i64 {
    let mut next = NEXT_HANDLE.lock();
    let handle = *next;
    *next += 1;
    SESSIONS.lock().insert(handle, session);
    handle
}

pub fn with_session<F, R>(handle: i64, f: F) -> Option<R>
where
    F: FnOnce(&CaptureSession) -> R,
{
    let sessions = SESSIONS.lock();
    sessions.get(&handle).map(f)
}

pub fn remove(handle: i64) {
    SESSIONS.lock().remove(&handle);
}
