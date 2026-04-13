#include "framebuffer_bridge.h"
#include "../utils/vine_log.h"
#include "../utils/vine_utils.h"

#include <cerrno>
#include <cstring>
#include <atomic>
#include <thread>
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

// ─── open / close ─────────────────────────────────────────────────────────────

bool FramebufferBridge::open() {
    if (!vine::path_exists(fb_device_path_)) {
        VINE_LOGE("Framebuffer device not found: %s", fb_device_path_.c_str());
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
              instance_id_.c_str(), guest_width_, guest_height_, (int)format_, guest_stride_);
    return true;
}

void FramebufferBridge::close() {
    stop_render_loop();
    munmap_fb();
    if (fb_fd_ >= 0) { ::close(fb_fd_); fb_fd_ = -1; }
}

// ─── set_surface ──────────────────────────────────────────────────────────────

void FramebufferBridge::set_surface(ANativeWindow* window) {
    if (window_) ANativeWindow_release(window_);
    window_ = window;
    if (window_) {
        ANativeWindow_acquire(window_);
        // AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM = 1
        ANativeWindow_setBuffersGeometry(window_, guest_width_, guest_height_, 1);
        VINE_LOGI("FramebufferBridge[%s]: surface attached %dx%d",
                  instance_id_.c_str(), guest_width_, guest_height_);
    }
}

// ─── query_fb_geometry ────────────────────────────────────────────────────────

bool FramebufferBridge::query_fb_geometry() {
    struct fb_var_screeninfo vinfo{};
    struct fb_fix_screeninfo finfo{};

    if (ioctl(fb_fd_, FBIOGET_VSCREENINFO, &vinfo) != 0) {
        VINE_LOGE_ERRNO("FBIOGET_VSCREENINFO");
        // Fall back to ranchu defaults (1080x1920 RGBA_8888)
        guest_width_  = 1080;
        guest_height_ = 1920;
        guest_stride_ = guest_width_ * 4;
        format_       = FrameFormat::RGBA_8888;
        frame_size_   = (size_t)guest_stride_ * guest_height_;
        VINE_LOGW("Using fallback framebuffer geometry %dx%d", guest_width_, guest_height_);
        return true;
    }

    if (ioctl(fb_fd_, FBIOGET_FSCREENINFO, &finfo) != 0) {
        VINE_LOGE_ERRNO("FBIOGET_FSCREENINFO");
        return false;
    }

    guest_width_  = (int)vinfo.xres;
    guest_height_ = (int)vinfo.yres;
    guest_stride_ = (int)finfo.line_length;
    frame_size_   = (size_t)finfo.smem_len;

    // Determine pixel format from bits_per_pixel and channel offsets
    if (vinfo.bits_per_pixel == 16) {
        format_ = FrameFormat::RGB_565;
    } else if (vinfo.bits_per_pixel == 32) {
        // Distinguish RGBA vs BGRA by red channel offset
        if (vinfo.red.offset == 0) {
            format_ = FrameFormat::RGBA_8888;
        } else {
            format_ = FrameFormat::BGRA_8888;
        }
    } else {
        VINE_LOGW("Unknown bpp=%d, assuming RGBA_8888", vinfo.bits_per_pixel);
        format_ = FrameFormat::RGBA_8888;
    }

    VINE_LOGI("FB geometry: %dx%d stride=%d bpp=%d fmt=%d smem_len=%zu",
              guest_width_, guest_height_, guest_stride_,
              vinfo.bits_per_pixel, (int)format_, frame_size_);
    return true;
}

// ─── mmap / munmap ────────────────────────────────────────────────────────────

bool FramebufferBridge::mmap_fb() {
    if (frame_size_ == 0) { VINE_LOGE("frame_size_ is 0, cannot mmap"); return false; }

    fb_mmap_ = mmap(nullptr, frame_size_, PROT_READ, MAP_SHARED, fb_fd_, 0);
    if (fb_mmap_ == MAP_FAILED) {
        VINE_LOGE_ERRNO("mmap(framebuffer)");
        fb_mmap_ = nullptr;
        return false;
    }
    VINE_LOGD("FramebufferBridge: mmap %zu bytes at %p", frame_size_, fb_mmap_);
    return true;
}

void FramebufferBridge::munmap_fb() {
    if (fb_mmap_ && fb_mmap_ != MAP_FAILED) {
        munmap(fb_mmap_, frame_size_);
        fb_mmap_ = nullptr;
    }
}

// ─── render_frame ─────────────────────────────────────────────────────────────

bool FramebufferBridge::render_frame() {
    if (!window_ || !fb_mmap_ || fb_fd_ < 0) return false;
    return blit_to_window();
}

bool FramebufferBridge::blit_to_window() {
    ANativeWindow_Buffer buffer{};
    ARect bounds = {0, 0, guest_width_, guest_height_};

    if (ANativeWindow_lock(window_, &buffer, &bounds) != 0) {
        VINE_LOGW("ANativeWindow_lock failed");
        return false;
    }

    const uint8_t* src = static_cast<const uint8_t*>(fb_mmap_);
    uint8_t* dst = static_cast<uint8_t*>(buffer.bits);
    const int dst_stride_bytes = buffer.stride * 4; // ANativeWindow stride is in pixels

    if (format_ == FrameFormat::RGB_565) {
        // Convert RGB565 → RGBA8888 row by row
        for (int y = 0; y < guest_height_ && y < buffer.height; ++y) {
            convert_rgb565_to_rgba8888(
                src + y * guest_stride_,
                dst + y * dst_stride_bytes,
                std::min(guest_width_, buffer.width),
                1,
                guest_stride_,
            );
        }
    } else if (format_ == FrameFormat::BGRA_8888) {
        // Swap B and R channels (BGRA → RGBA)
        for (int y = 0; y < guest_height_ && y < buffer.height; ++y) {
            const uint32_t* s = reinterpret_cast<const uint32_t*>(src + y * guest_stride_);
            uint32_t* d = reinterpret_cast<uint32_t*>(dst + y * dst_stride_bytes);
            const int w = std::min(guest_width_, buffer.width);
            for (int x = 0; x < w; ++x) {
                uint32_t px = s[x];
                // BGRA: B=bits[0..7] G=bits[8..15] R=bits[16..23] A=bits[24..31]
                // RGBA: R=bits[0..7] G=bits[8..15] B=bits[16..23] A=bits[24..31]
                d[x] = ((px & 0x000000FF) << 16) | // B → R
                       ( px & 0x0000FF00)         | // G stays
                       ((px & 0x00FF0000) >> 16)  | // R → B
                       ( px & 0xFF000000);           // A stays
            }
        }
    } else {
        // RGBA_8888: direct row copy (handling stride differences)
        const int copy_bytes = std::min(guest_width_, buffer.width) * 4;
        for (int y = 0; y < guest_height_ && y < buffer.height; ++y) {
            memcpy(
                dst + y * dst_stride_bytes,
                src + y * guest_stride_,
                copy_bytes,
            );
        }
    }

    ANativeWindow_unlockAndPost(window_);
    return true;
}

// ─── render loop ──────────────────────────────────────────────────────────────

bool FramebufferBridge::start_render_loop() {
    if (rendering_.load()) return true;
    if (!is_open()) {
        VINE_LOGE("start_render_loop: framebuffer not open");
        return false;
    }
    rendering_.store(true);

    render_thread_ = std::thread([this]() {
        VINE_LOGI("FramebufferBridge[%s]: render loop started", instance_id_.c_str());

        // Target ~60fps. We use a simple sleep-based loop for Phase 2.
        // Phase 3 TODO: sync to Choreographer vsync via AChoreographer API.
        constexpr int TARGET_FRAME_US = 16667; // 1s / 60fps in microseconds

        while (rendering_.load()) {
            auto frame_start = std::chrono::steady_clock::now();

            if (window_ && fb_mmap_) {
                blit_to_window();
            }

            auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now() - frame_start
            ).count();

            if (elapsed < TARGET_FRAME_US) {
                usleep((useconds_t)(TARGET_FRAME_US - elapsed));
            }
        }

        VINE_LOGI("FramebufferBridge[%s]: render loop stopped", instance_id_.c_str());
    });

    return true;
}

void FramebufferBridge::stop_render_loop() {
    if (!rendering_.load()) return;
    rendering_.store(false);
    if (render_thread_.joinable()) render_thread_.join();
}

// ─── RGB565 → RGBA8888 ────────────────────────────────────────────────────────

void FramebufferBridge::convert_rgb565_to_rgba8888(
        const void* src, void* dst, int width, int height, int src_stride) {
    const uint16_t* s = static_cast<const uint16_t*>(src);
    uint32_t* d = static_cast<uint32_t*>(dst);
    const int src_row_pixels = src_stride / 2;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            uint16_t px = s[y * src_row_pixels + x];
            uint8_t r = (px >> 11) & 0x1F; r = (r << 3) | (r >> 2);
            uint8_t g = (px >>  5) & 0x3F; g = (g << 2) | (g >> 4);
            uint8_t b =  px        & 0x1F; b = (b << 3) | (b >> 2);
            d[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
    }
}

} // namespace vine::display
