#include "framebuffer_bridge.h"
#include "../utils/vine_log.h"
#include "../utils/vine_utils.h"

#include <cerrno>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <linux/fb.h>
#include <android/native_window.h>
#include <thread>
#include <atomic>

namespace vine::display {

FramebufferBridge::FramebufferBridge(
        std::string instance_id,
        std::string fb_device_path)
    : instance_id_(std::move(instance_id))
    , fb_device_path_(std::move(fb_device_path)) {}

FramebufferBridge::~FramebufferBridge() {
    stop_render_loop();
    close();
}

// ─── open() ──────────────────────────────────────────────────────────────────

bool FramebufferBridge::open() {
    VINE_LOGI("FramebufferBridge[%s]: opening %s",
              instance_id_.c_str(), fb_device_path_.c_str());

    if (!vine::path_exists(fb_device_path_)) {
        VINE_LOGE("Framebuffer device does not exist: %s — guest may not be ready yet",
                  fb_device_path_.c_str());
        return false;
    }

    fb_fd_ = ::open(fb_device_path_.c_str(), O_RDWR | O_CLOEXEC);
    if (fb_fd_ < 0) {
        VINE_LOGE_ERRNO(("open(" + fb_device_path_ + ")").c_str());
        return false;
    }

    if (!query_fb_geometry()) {
        ::close(fb_fd_);
        fb_fd_ = -1;
        return false;
    }

    if (!mmap_fb()) {
        ::close(fb_fd_);
        fb_fd_ = -1;
        return false;
    }

    VINE_LOGI("FramebufferBridge[%s]: opened %dx%d fmt=%d stride=%d",
              instance_id_.c_str(), guest_width_, guest_height_,
              (int)format_, guest_stride_);
    return true;
}

// ─── close() ─────────────────────────────────────────────────────────────────

void FramebufferBridge::close() {
    munmap_fb();
    if (fb_fd_ >= 0) {
        ::close(fb_fd_);
        fb_fd_ = -1;
    }
    VINE_LOGD("FramebufferBridge[%s]: closed", instance_id_.c_str());
}

// ─── set_surface() ───────────────────────────────────────────────────────────

void FramebufferBridge::set_surface(ANativeWindow* window) {
    if (window_) ANativeWindow_release(window_);
    window_ = window;
    if (window_) {
        ANativeWindow_acquire(window_);
        // Set the buffer geometry to match the guest framebuffer.
        // AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM = 1 (matches RGBA_8888)
        ANativeWindow_setBuffersGeometry(window_, guest_width_, guest_height_, 1);
        VINE_LOGI("FramebufferBridge[%s]: surface attached %dx%d",
                  instance_id_.c_str(), guest_width_, guest_height_);
    } else {
        VINE_LOGD("FramebufferBridge[%s]: surface detached", instance_id_.c_str());
    }
}

// ─── query_fb_geometry() ─────────────────────────────────────────────────────

bool FramebufferBridge::query_fb_geometry() {
    // Phase 2 TODO: Implement using FBIOGET_VSCREENINFO / FBIOGET_FSCREENINFO
    // For now, use hardcoded defaults matching Android 7.x on ranchu target.
    // The ranchu virtual device defaults to 1080x1920 RGBA_8888.

    // Real implementation:
    // struct fb_var_screeninfo vinfo{};
    // struct fb_fix_screeninfo finfo{};
    // if (ioctl(fb_fd_, FBIOGET_VSCREENINFO, &vinfo) != 0) { ... error ... }
    // if (ioctl(fb_fd_, FBIOGET_FSCREENINFO, &finfo) != 0) { ... error ... }
    // guest_width_  = vinfo.xres;
    // guest_height_ = vinfo.yres;
    // guest_stride_ = finfo.line_length;
    // Determine format from vinfo.bits_per_pixel, vinfo.red/green/blue offsets:
    //   32bpp, R=16,G=8,B=0,A=24 → RGBA_8888
    //   32bpp, B=16,G=8,R=0,A=24 → BGRA_8888
    //   16bpp, R=11,G=5,B=0      → RGB_565

    // Placeholder defaults:
    guest_width_  = 1080;
    guest_height_ = 1920;
    guest_stride_ = guest_width_ * 4; // 4 bytes per pixel for RGBA_8888
    format_       = FrameFormat::RGBA_8888;
    frame_size_   = static_cast<size_t>(guest_stride_) * guest_height_;

    VINE_LOGD("FramebufferBridge: geometry %dx%d stride=%d (placeholder)",
              guest_width_, guest_height_, guest_stride_);
    return true;
}

// ─── mmap_fb() ───────────────────────────────────────────────────────────────

bool FramebufferBridge::mmap_fb() {
    // Phase 2 TODO: Actually mmap the framebuffer fd for zero-copy reads.
    // fb_mmap_ = mmap(nullptr, frame_size_, PROT_READ, MAP_SHARED, fb_fd_, 0);
    // if (fb_mmap_ == MAP_FAILED) { VINE_LOGE_ERRNO("mmap(fb)"); return false; }
    VINE_LOGD("FramebufferBridge: mmap placeholder (Phase 2)");
    fb_mmap_ = nullptr; // Placeholder
    return true;
}

void FramebufferBridge::munmap_fb() {
    if (fb_mmap_ && fb_mmap_ != MAP_FAILED) {
        munmap(fb_mmap_, frame_size_);
        fb_mmap_ = nullptr;
    }
}

// ─── render_frame() ──────────────────────────────────────────────────────────

bool FramebufferBridge::render_frame() {
    if (!window_ || !fb_mmap_ || fb_fd_ < 0) return false;
    return blit_to_window();
}

bool FramebufferBridge::blit_to_window() {
    // Phase 2 TODO: Full implementation.
    //
    // 1. Lock the ANativeWindow buffer:
    //    ANativeWindow_Buffer buffer{};
    //    ARect bounds = {0, 0, guest_width_, guest_height_};
    //    if (ANativeWindow_lock(window_, &buffer, &bounds) != 0) return false;
    //
    // 2. Copy from fb_mmap_ to buffer.bits with format conversion if needed:
    //    if (format_ == FrameFormat::RGB_565) {
    //        convert_rgb565_to_rgba8888(fb_mmap_, buffer.bits,
    //                                   guest_width_, guest_height_, guest_stride_);
    //    } else {
    //        // Same format: direct memcpy row by row (handle stride differences)
    //        uint8_t* src = (uint8_t*)fb_mmap_;
    //        uint8_t* dst = (uint8_t*)buffer.bits;
    //        for (int row = 0; row < guest_height_; ++row) {
    //            memcpy(dst + row * buffer.stride * 4,
    //                   src + row * guest_stride_,
    //                   guest_width_ * 4);
    //        }
    //    }
    //
    // 3. Unlock and post:
    //    ANativeWindow_unlockAndPost(window_);
    //
    // 4. Return true.

    VINE_LOGD("FramebufferBridge::blit_to_window: Phase 2 placeholder");
    return false;
}

// ─── start/stop_render_loop() ────────────────────────────────────────────────

bool FramebufferBridge::start_render_loop() {
    if (rendering_) return true;
    // Phase 2 TODO: Spawn a render thread that calls render_frame() at ~60fps,
    // using vsync signals from Choreographer or a simple sleep(16ms) fallback.
    rendering_ = false; // Will be true once Phase 2 is implemented
    VINE_LOGD("FramebufferBridge::start_render_loop: Phase 2 placeholder");
    return false;
}

void FramebufferBridge::stop_render_loop() {
    rendering_ = false;
    // Phase 2 TODO: signal render thread to stop and join it
}

// ─── RGB565 → RGBA8888 ────────────────────────────────────────────────────────

void FramebufferBridge::convert_rgb565_to_rgba8888(
        const void* src, void* dst,
        int width, int height, int src_stride) {
    const uint16_t* s = static_cast<const uint16_t*>(src);
    uint32_t* d = static_cast<uint32_t*>(dst);
    const int src_row_pixels = src_stride / 2;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            uint16_t px = s[y * src_row_pixels + x];
            uint8_t r = (px >> 11) & 0x1F;
            uint8_t g = (px >> 5)  & 0x3F;
            uint8_t b =  px        & 0x1F;
            // Expand to 8 bits per channel
            r = (r << 3) | (r >> 2);
            g = (g << 2) | (g >> 4);
            b = (b << 3) | (b >> 2);
            d[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
    }
}

} // namespace vine::display
