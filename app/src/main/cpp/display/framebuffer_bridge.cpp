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

namespace vine::display {

FramebufferBridge::FramebufferBridge(std::string instance_id, std::string fb_device_path)
    : instance_id_(std::move(instance_id)), fb_device_path_(std::move(fb_device_path)) {}

FramebufferBridge::~FramebufferBridge() {
    stop_render_loop();
    close();
}

bool FramebufferBridge::open() {
    if (!vine::path_exists(fb_device_path_)) {
        VINE_LOGE("Framebuffer device not found: %s", fb_device_path_.c_str());
        return false;
    }
    fb_fd_ = ::open(fb_device_path_.c_str(), O_RDWR | O_CLOEXEC);
    if (fb_fd_ < 0) { VINE_LOGE_ERRNO(("open(" + fb_device_path_ + ")").c_str()); return false; }
    if (!query_fb_geometry()) { ::close(fb_fd_); fb_fd_ = -1; return false; }
    if (!mmap_fb()) { ::close(fb_fd_); fb_fd_ = -1; return false; }
    VINE_LOGI("FramebufferBridge[%s]: %dx%d fmt=%d", instance_id_.c_str(), guest_width_, guest_height_, (int)format_);
    return true;
}

void FramebufferBridge::close() {
    munmap_fb();
    if (fb_fd_ >= 0) { ::close(fb_fd_); fb_fd_ = -1; }
}

void FramebufferBridge::set_surface(ANativeWindow* window) {
    if (window_) ANativeWindow_release(window_);
    window_ = window;
    if (window_) {
        ANativeWindow_acquire(window_);
        ANativeWindow_setBuffersGeometry(window_, guest_width_, guest_height_, 1);
    }
}

bool FramebufferBridge::query_fb_geometry() {
    // TODO Phase 2: use FBIOGET_VSCREENINFO/FBIOGET_FSCREENINFO ioctls
    guest_width_  = 1080;
    guest_height_ = 1920;
    guest_stride_ = guest_width_ * 4;
    format_       = FrameFormat::RGBA_8888;
    frame_size_   = (size_t)guest_stride_ * guest_height_;
    return true;
}

bool FramebufferBridge::mmap_fb() {
    // TODO Phase 2: mmap(nullptr, frame_size_, PROT_READ, MAP_SHARED, fb_fd_, 0)
    fb_mmap_ = nullptr;
    return true;
}

void FramebufferBridge::munmap_fb() {
    if (fb_mmap_ && fb_mmap_ != MAP_FAILED) {
        munmap(fb_mmap_, frame_size_);
        fb_mmap_ = nullptr;
    }
}

bool FramebufferBridge::render_frame() {
    if (!window_ || !fb_mmap_ || fb_fd_ < 0) return false;
    return blit_to_window();
}

bool FramebufferBridge::blit_to_window() {
    // TODO Phase 2: ANativeWindow_lock → memcpy/convert → ANativeWindow_unlockAndPost
    return false;
}

bool FramebufferBridge::start_render_loop() {
    // TODO Phase 2: spawn render thread at ~60fps
    return false;
}

void FramebufferBridge::stop_render_loop() {
    rendering_ = false;
}

void FramebufferBridge::convert_rgb565_to_rgba8888(
        const void* src, void* dst, int width, int height, int src_stride) {
    const uint16_t* s = static_cast<const uint16_t*>(src);
    uint32_t* d = static_cast<uint32_t*>(dst);
    const int src_row_pixels = src_stride / 2;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            uint16_t px = s[y * src_row_pixels + x];
            uint8_t r = ((px >> 11) & 0x1F); r = (r << 3) | (r >> 2);
            uint8_t g = ((px >>  5) & 0x3F); g = (g << 2) | (g >> 4);
            uint8_t b = ( px        & 0x1F); b = (b << 3) | (b >> 2);
            d[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
    }
}

} // namespace vine::display
