//! Captures frames from a specific HWND using the modern
//! Windows.Graphics.Capture API (the same API OBS/Xbox Game Bar use).
//! Each captured frame is copied into a CPU-readable BGRA8 buffer that
//! the JNI layer hands to Java as a direct ByteBuffer for texture upload.

use std::sync::Arc;
use parking_lot::Mutex;

use windows::core::Interface;
use windows::Foundation::TypedEventHandler;
use windows::Graphics::Capture::{
    Direct3D11CaptureFramePool, GraphicsCaptureItem, GraphicsCaptureSession,
};
use windows::Graphics::DirectX::DirectXPixelFormat;
use windows::Graphics::SizeInt32;
use windows::Win32::Foundation::HWND;
use windows::Win32::Graphics::Direct3D::D3D_DRIVER_TYPE_HARDWARE;
use windows::Win32::Graphics::Direct3D11::{
    D3D11CreateDevice, ID3D11Device, ID3D11DeviceContext, ID3D11Texture2D,
    D3D11_CREATE_DEVICE_BGRA_SUPPORT, D3D11_SDK_VERSION,
    D3D11_CPU_ACCESS_READ, D3D11_MAP_READ, D3D11_MAPPED_SUBRESOURCE,
    D3D11_TEXTURE2D_DESC, D3D11_USAGE_STAGING,
};
use windows::Win32::System::WinRT::Direct3D11::{
    CreateDirect3D11DeviceFromDXGIDevice, IDirect3DDxgiInterfaceAccess,
};
use windows::Win32::System::WinRT::Graphics::Capture::IGraphicsCaptureItemInterop;
use windows::Win32::Graphics::Dxgi::IDXGIDevice;

pub struct FrameData {
    pub width: u32,
    pub height: u32,
    /// Tightly packed BGRA8 pixels, row-major, top-down.
    pub pixels: Vec<u8>,
}

pub struct CaptureSession {
    _item: GraphicsCaptureItem,
    _frame_pool: Direct3D11CaptureFramePool,
    session: GraphicsCaptureSession,
    d3d_device: ID3D11Device,
    d3d_context: ID3D11DeviceContext,
    latest_frame: Arc<Mutex<Option<FrameData>>>,
}

impl CaptureSession {
    /// Starts capturing the given HWND. Returns None if the window can't
    /// be captured (e.g. protected content, or capture API unsupported
    /// on this Windows build).
    pub fn start(hwnd: isize) -> windows::core::Result<Self> {
        unsafe {
            // 1. Create a D3D11 device (hardware, BGRA-capable so it can
            //    back a WinRT capture surface).
            let mut d3d_device: Option<ID3D11Device> = None;
            let mut d3d_context: Option<ID3D11DeviceContext> = None;
            D3D11CreateDevice(
                None,
                D3D_DRIVER_TYPE_HARDWARE,
                None,
                D3D11_CREATE_DEVICE_BGRA_SUPPORT,
                None,
                D3D11_SDK_VERSION,
                Some(&mut d3d_device),
                None,
                Some(&mut d3d_context),
            )?;
            let d3d_device = d3d_device.unwrap();
            let d3d_context = d3d_context.unwrap();

            // 2. Wrap it as a WinRT IDirect3DDevice for the capture APIs.
            let dxgi_device: IDXGIDevice = d3d_device.cast()?;
            let winrt_device = CreateDirect3D11DeviceFromDXGIDevice(&dxgi_device)?;
            let winrt_device: windows::Graphics::DirectX::Direct3D11::IDirect3DDevice =
                winrt_device.cast()?;

            // 3. Create a GraphicsCaptureItem for the target HWND.
            let interop: IGraphicsCaptureItemInterop =
                windows::core::factory::<GraphicsCaptureItem, IGraphicsCaptureItemInterop>()?;
            let item: GraphicsCaptureItem = interop.CreateForWindow(HWND(hwnd as *mut _))?;

            let size: SizeInt32 = item.Size()?;

            // 4. Frame pool + session.
            let frame_pool = Direct3D11CaptureFramePool::Create(
                &winrt_device,
                DirectXPixelFormat::B8G8R8A8UIntNormalized,
                2, // double-buffered
                size,
            )?;

            let session = frame_pool.CreateCaptureSession(&item)?;

            let latest_frame: Arc<Mutex<Option<FrameData>>> = Arc::new(Mutex::new(None));

            {
                let latest_frame = latest_frame.clone();
                let d3d_device = d3d_device.clone();
                let d3d_context = d3d_context.clone();
                frame_pool.FrameArrived(&TypedEventHandler::new(
                    move |pool: &Option<Direct3D11CaptureFramePool>, _| {
                        if let Some(pool) = pool {
                            if let Ok(frame) = pool.TryGetNextFrame() {
                                if let Ok(data) =
                                    copy_frame_to_cpu(&d3d_device, &d3d_context, &frame)
                                {
                                    *latest_frame.lock() = Some(data);
                                }
                            }
                        }
                        Ok(())
                    },
                ))?;
            }

            session.StartCapture()?;

            Ok(Self {
                _item: item,
                _frame_pool: frame_pool,
                session,
                d3d_device,
                d3d_context,
                latest_frame,
            })
        }
    }

    /// Non-blocking: returns the most recently captured frame, if any
    /// new one has arrived since the last call.
    pub fn take_latest_frame(&self) -> Option<FrameData> {
        self.latest_frame.lock().take()
    }

    pub fn stop(&self) {
        let _ = self.session.Close();
    }
}

impl Drop for CaptureSession {
    fn drop(&mut self) {
        self.stop();
    }
}

/// Copies a captured GPU surface into a CPU-accessible staging texture
/// and reads it back into a flat BGRA8 buffer.
unsafe fn copy_frame_to_cpu(
    device: &ID3D11Device,
    context: &ID3D11DeviceContext,
    frame: &windows::Graphics::Capture::Direct3D11CaptureFrame,
) -> windows::core::Result<FrameData> {
    let surface = frame.Surface()?;
    let access: IDirect3DDxgiInterfaceAccess = surface.cast()?;
    let src_texture: ID3D11Texture2D = access.GetInterface()?;

    let mut desc = D3D11_TEXTURE2D_DESC::default();
    src_texture.GetDesc(&mut desc);

    let staging_desc = D3D11_TEXTURE2D_DESC {
        Usage: D3D11_USAGE_STAGING,
        CPUAccessFlags: D3D11_CPU_ACCESS_READ.0 as u32,
        BindFlags: 0,
        MiscFlags: 0,
        ..desc
    };

    let mut staging: Option<ID3D11Texture2D> = None;
    device.CreateTexture2D(&staging_desc, None, Some(&mut staging))?;
    let staging = staging.unwrap();

    context.CopyResource(&staging, &src_texture);

    let mut mapped = D3D11_MAPPED_SUBRESOURCE::default();
    context.Map(&staging, 0, D3D11_MAP_READ, 0, Some(&mut mapped))?;
    let width = desc.Width;
    let height = desc.Height;
    let row_pitch = mapped.RowPitch as usize;
    let mut pixels = vec![0u8; (width * height * 4) as usize];

    let src_ptr = mapped.pData as *const u8;
    for y in 0..height as usize {
        let src_row = src_ptr.add(y * row_pitch);
        let dst_row = pixels.as_mut_ptr().add(y * width as usize * 4);
        std::ptr::copy_nonoverlapping(src_row, dst_row, width as usize * 4);
    }

    context.Unmap(&staging, 0);

    Ok(FrameData {
        width,
        height,
        pixels,
    })
}
