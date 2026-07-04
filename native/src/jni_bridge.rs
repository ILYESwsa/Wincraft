//! JNI entry points. Method names must exactly match the Java native
//! method signatures in `com.wincraft.native.WincraftNative` — see
//! fabric/src/main/java/com/wincraft/native/WincraftNative.java.

use jni::objects::{JClass, JObject, JValue};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jobjectArray, JNI_TRUE};
use jni::JNIEnv;

use crate::{capture::CaptureSession, enumerate, input, session};

/// Lists capturable windows. Returns a Java `WindowHandle[]` array
/// (see WindowHandle.java: hwnd, title, className).
#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_enumerateWindows(
    mut env: JNIEnv,
    _class: JClass,
) -> jobjectArray {
    let windows = enumerate::enumerate_windows();

    let handle_class = match env.find_class("com/wincraft/natives/WindowHandle") {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let array = match env.new_object_array(windows.len() as i32, &handle_class, JObject::null()) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };

    for (i, w) in windows.iter().enumerate() {
        let title = match env.new_string(&w.title) {
            Ok(s) => s,
            Err(_) => continue,
        };
        let class_name = match env.new_string(&w.class_name) {
            Ok(s) => s,
            Err(_) => continue,
        };

        let obj = env.new_object(
            &handle_class,
            "(JLjava/lang/String;Ljava/lang/String;)V",
            &[
                JValue::Long(w.hwnd as jlong),
                JValue::Object(&title),
                JValue::Object(&class_name),
            ],
        );

        if let Ok(obj) = obj {
            let _ = env.set_object_array_element(&array, i as i32, obj);
        }
    }

    array.into_raw()
}

/// Starts capturing the given HWND. Returns an opaque session handle,
/// or 0 on failure.
#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_startCapture(
    _env: JNIEnv,
    _class: JClass,
    hwnd: jlong,
) -> jlong {
    match CaptureSession::start(hwnd as isize) {
        Ok(s) => session::register(s),
        Err(_) => 0,
    }
}

/// Stops and releases a capture session.
#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_stopCapture(
    _env: JNIEnv,
    _class: JClass,
    session_handle: jlong,
) {
    session::remove(session_handle);
}

/// Pulls the latest available frame as a flat BGRA8 byte array, plus
/// width/height written into the provided int[2] out-param.
/// Returns null if no new frame is available since the last call.
#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_pollFrame(
    mut env: JNIEnv,
    _class: JClass,
    session_handle: jlong,
    dims_out: jni::sys::jintArray,
) -> jbyteArray {
    // with_session returns Option<Option<FrameData>>: outer None means
    // the handle doesn't exist, inner None means no new frame yet.
    let frame = match session::with_session(session_handle, |s| s.take_latest_frame()) {
        Some(Some(f)) => f,
        _ => return std::ptr::null_mut(),
    };

    let dims_arr = unsafe { JObject::from_raw(dims_out) };
    let dims = jni::objects::JIntArray::from(dims_arr);
    let dims_vals: [jint; 2] = [frame.width as jint, frame.height as jint];
    if env.set_int_array_region(&dims, 0, &dims_vals).is_err() {
        return std::ptr::null_mut();
    }

    let byte_array = match env.byte_array_from_slice(&frame.pixels) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };

    byte_array.into_raw()
}

fn input_mode_from_jint(mode: jint) -> input::InputMode {
    if mode == 1 {
        input::InputMode::RawForeground
    } else {
        input::InputMode::Windowed
    }
}

#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_mouseMove(
    _env: JNIEnv,
    _class: JClass,
    hwnd: jlong,
    mode: jint,
    x: jint,
    y: jint,
) {
    input::mouse_move(hwnd as isize, input_mode_from_jint(mode), x, y);
}

#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_mouseButton(
    _env: JNIEnv,
    _class: JClass,
    hwnd: jlong,
    mode: jint,
    button: jint,
    down: jboolean,
    x: jint,
    y: jint,
) {
    let btn = if button == 0 {
        input::MouseButton::Left
    } else {
        input::MouseButton::Right
    };
    input::mouse_button(
        hwnd as isize,
        input_mode_from_jint(mode),
        btn,
        down == JNI_TRUE,
        x,
        y,
    );
}

#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_mouseWheel(
    _env: JNIEnv,
    _class: JClass,
    hwnd: jlong,
    mode: jint,
    delta: jint,
    x: jint,
    y: jint,
) {
    input::mouse_wheel(hwnd as isize, input_mode_from_jint(mode), delta, x, y);
}

#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_keyEvent(
    _env: JNIEnv,
    _class: JClass,
    hwnd: jlong,
    mode: jint,
    vk_code: jint,
    down: jboolean,
) {
    input::key_event(
        hwnd as isize,
        input_mode_from_jint(mode),
        vk_code as u16,
        down == JNI_TRUE,
    );
}

#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_charEvent(
    _env: JNIEnv,
    _class: JClass,
    hwnd: jlong,
    ch: jint,
) {
    input::char_event(hwnd as isize, ch as u16);
}

/// Simple liveness check exposed to Java so the mod can verify the
/// native library loaded correctly before doing anything else.
#[no_mangle]
pub extern "system" fn Java_com_wincraft_natives_WincraftNative_ping(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    JNI_TRUE
}
