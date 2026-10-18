#include "framebuffer_bridge.h"
#include "../utils/ypsilon_log.h"
#include "../utils/ypsilon_utils.h"

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <atomic>
#include <thread>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <linux/fb.h>
#include <android/looper.h>
#include <android/native_window.h>

namespace ypsilon::display {

FramebufferBridge::FramebufferBridge(std::string instance_id, std::string fb_device_path)
    : instance_id_(std::move(instance_id)), fb_device_path_(std::move(fb_device_path)) {}

FramebufferBridge::~FramebufferBridge() {
    stop_render_loop();
    close();
}

bool FramebufferBridge::open() {
    if (!ypsilon::path_exists(fb_device_path_)) {
        YPSILON_LOGE("Framebuffer device not found: %s", fb_device_path_.c_str());
        return false;
    }
    fb_fd_ = ::open(fb_device_path_.c_str(), O_RDWR | O_CLOEXEC);
    if (fb_fd_ < 0) {
        YPSILON_LOGE_ERRNO(("open(" + fb_device_path_ + ")").c_str());
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
    YPSILON_LOGI("FramebufferBridge[%s]: opened %dx%d fmt=%d stride=%d",
              instance_id_.c_str(), guest_width_, guest_height_, (int)format_, guest_stride_);
    return true;
}

void FramebufferBridge::close() {
    stop_render_loop();
    munmap_fb();
    if (fb_fd_ >= 0) { ::close(fb_fd_); fb_fd_ = -1; }
}

void FramebufferBridge::set_surface(ANativeWindow* window) {
    if (window_) ANativeWindow_release(window_);
    window_ = window;
    if (window_) {
        ANativeWindow_acquire(window_);
        // AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM = 1
        ANativeWindow_setBuffersGeometry(window_, guest_width_, guest_height_, 1);
        YPSILON_LOGI("FramebufferBridge[%s]: surface attached %dx%d",
                  instance_id_.c_str(), guest_width_, guest_height_);
    }
}

void FramebufferBridge::set_render_scale(float scale) {
    render_scale_ = std::clamp(scale, 0.25f, 1.0f);
    YPSILON_LOGI("FramebufferBridge[%s]: render scale → %.2f",
                 instance_id_.c_str(), render_scale_);
}

bool FramebufferBridge::query_fb_geometry() {
    struct fb_var_screeninfo vinfo{};
    struct fb_fix_screeninfo finfo{};

    if (ioctl(fb_fd_, FBIOGET_VSCREENINFO, &vinfo) != 0) {
        YPSILON_LOGE_ERRNO("FBIOGET_VSCREENINFO");
        // Fall back to ranchu defaults (1080x1920 RGBA_8888)
        guest_width_  = 1080;
        guest_height_ = 1920;
        guest_stride_ = guest_width_ * 4;
        format_       = FrameFormat::RGBA_8888;
        frame_size_   = (size_t)guest_stride_ * guest_height_;
        YPSILON_LOGW("Using fallback framebuffer geometry %dx%d", guest_width_, guest_height_);
        return true;
    }

    if (ioctl(fb_fd_, FBIOGET_FSCREENINFO, &finfo) != 0) {
        YPSILON_LOGE_ERRNO("FBIOGET_FSCREENINFO");
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
        YPSILON_LOGW("Unknown bpp=%d, assuming RGBA_8888", vinfo.bits_per_pixel);
        format_ = FrameFormat::RGBA_8888;
    }

    YPSILON_LOGI("FB geometry: %dx%d stride=%d bpp=%d fmt=%d smem_len=%zu",
              guest_width_, guest_height_, guest_stride_,
              vinfo.bits_per_pixel, (int)format_, frame_size_);
    return true;
}

bool FramebufferBridge::mmap_fb() {
    if (frame_size_ == 0) { YPSILON_LOGE("frame_size_ is 0, cannot mmap"); return false; }

    fb_mmap_ = mmap(nullptr, frame_size_, PROT_READ, MAP_SHARED, fb_fd_, 0);
    if (fb_mmap_ == MAP_FAILED) {
        YPSILON_LOGE_ERRNO("mmap(framebuffer)");
        fb_mmap_ = nullptr;
        return false;
    }
    YPSILON_LOGD("FramebufferBridge: mmap %zu bytes at %p", frame_size_, fb_mmap_);
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
    ANativeWindow_Buffer buffer{};
    ARect bounds = {0, 0, guest_width_, guest_height_};

    if (ANativeWindow_lock(window_, &buffer, &bounds) != 0) {
        YPSILON_LOGW("ANativeWindow_lock failed");
        return false;
    }

    const uint8_t* src = static_cast<const uint8_t*>(fb_mmap_);
    uint8_t* dst = static_cast<uint8_t*>(buffer.bits);
    const int dst_stride_bytes = buffer.stride * 4; // ANativeWindow stride is in pixels

    // Effective render resolution. With render_scale_ < 1.0 we downscale the
    // guest framebuffer before blitting, saving fill-rate and memory bandwidth
    // on low-end devices. Box-filtered downsampling keeps the image crisp.
    const float scale = render_scale_;
    const int src_w = guest_width_;
    const int src_h = guest_height_;
    const int dst_w = std::max(1, (int)(src_w * scale));
    const int dst_h = std::max(1, (int)(src_h * scale));
    const int draw_w = std::min(dst_w, buffer.width);
    const int draw_h = std::min(dst_h, buffer.height);

    const int src_row_pixels = (guest_stride_ > 0) ? (guest_stride_ / 4) : src_w;

    if (format_ == FrameFormat::RGBA_8888 || format_ == FrameFormat::RGBX_8888) {
        const uint32_t* s = reinterpret_cast<const uint32_t*>(src);
        uint32_t* d = reinterpret_cast<uint32_t*>(dst);
        for (int dy = 0; dy < draw_h; ++dy) {
            const int sy0 = (dy * src_h) / draw_h;
            const int sy1 = ((dy + 1) * src_h) / draw_h;
            for (int dx = 0; dx < draw_w; ++dx) {
                const int sx0 = (dx * src_w) / draw_w;
                const int sx1 = ((dx + 1) * src_w) / draw_w;
                uint32_t r = 0, g = 0, b = 0, a = 0;
                int count = 0;
                for (int sy = sy0; sy < sy1 && sy < src_h; ++sy) {
                    const uint32_t* row = s + sy * src_row_pixels;
                    for (int sx = sx0; sx < sx1 && sx < src_w; ++sx) {
                        uint32_t px = row[sx];
                        r += (px >> 16) & 0xFF;
                        g += (px >> 8) & 0xFF;
                        b += px & 0xFF;
                        a += (px >> 24) & 0xFF;
                        ++count;
                    }
                }
                if (count > 0) {
                    d[dy * buffer.stride + dx] =
                        (a / count << 24) | (r / count << 16) | (g / count << 8) | (b / count);
                }
            }
        }
    } else if (format_ == FrameFormat::BGRA_8888) {
        const uint32_t* s = reinterpret_cast<const uint32_t*>(src);
        uint32_t* d = reinterpret_cast<uint32_t*>(dst);
        for (int dy = 0; dy < draw_h; ++dy) {
            const int sy0 = (dy * src_h) / draw_h;
            const int sy1 = ((dy + 1) * src_h) / draw_h;
            for (int dx = 0; dx < draw_w; ++dx) {
                const int sx0 = (dx * src_w) / draw_w;
                const int sx1 = ((dx + 1) * src_w) / draw_w;
                uint32_t r = 0, g = 0, b = 0, a = 0;
                int count = 0;
                for (int sy = sy0; sy < sy1 && sy < src_h; ++sy) {
                    const uint32_t* row = s + sy * src_row_pixels;
                    for (int sx = sx0; sx < sx1 && sx < src_w; ++sx) {
                        uint32_t px = row[sx];
                        r += (px >> 16) & 0xFF;
                        g += (px >> 8) & 0xFF;
                        b += px & 0xFF;
                        a += (px >> 24) & 0xFF;
                        ++count;
                    }
                }
                if (count > 0) {
                    d[dy * buffer.stride + dx] =
                        ((b / count) << 16) | (g / count << 8) | (r / count) | (a / count << 24);
                }
            }
        }
    } else if (format_ == FrameFormat::RGB_565) {
        const uint16_t* s = reinterpret_cast<const uint16_t*>(src);
        uint32_t* d = reinterpret_cast<uint32_t*>(dst);
        for (int dy = 0; dy < draw_h; ++dy) {
            const int sy0 = (dy * src_h) / draw_h;
            const int sy1 = ((dy + 1) * src_h) / draw_h;
            for (int dx = 0; dx < draw_w; ++dx) {
                const int sx0 = (dx * src_w) / draw_w;
                const int sx1 = ((dx + 1) * src_w) / draw_w;
                uint32_t r = 0, g = 0, b = 0;
                int count = 0;
                for (int sy = sy0; sy < sy1 && sy < src_h; ++sy) {
                    const uint16_t* row = s + sy * (guest_stride_ / 2);
                    for (int sx = sx0; sx < sx1 && sx < src_w; ++sx) {
                        uint16_t px = row[sx];
                        r += ((px >> 11) & 0x1F);
                        g += ((px >> 5) & 0x3F);
                        b += (px & 0x1F);
                        ++count;
                    }
                }
                if (count > 0) {
                    uint8_t R = (uint8_t)(((r / count) << 3) | ((r / count) >> 2));
                    uint8_t G = (uint8_t)(((g / count) << 2) | ((g / count) >> 4));
                    uint8_t B = (uint8_t)(((b / count) << 3) | ((b / count) >> 2));
                    d[dy * buffer.stride + dx] = (0xFF << 24) | (R << 16) | (G << 8) | B;
                }
            }
        }
    } else {
        // Unknown format: nearest-neighbor passthrough at native res.
        const int copy_w = std::min(src_w, buffer.width);
        const int copy_h = std::min(src_h, buffer.height);
        for (int y = 0; y < copy_h && y < draw_h; ++y) {
            memcpy(dst + y * dst_stride_bytes, src + y * guest_stride_, copy_w * 4);
        }
    }

    // Clear any area of the window not covered by the (possibly downscaled) frame.
    if (draw_h < buffer.height) {
        std::memset(dst + draw_h * dst_stride_bytes, 0,
                    (size_t)(buffer.height - draw_h) * dst_stride_bytes);
    }
    if (draw_w < buffer.width) {
        for (int y = 0; y < draw_h; ++y) {
            std::memset(dst + y * dst_stride_bytes + draw_w * 4, 0,
                        (size_t)(buffer.width - draw_w) * 4);
        }
    }

    ANativeWindow_unlockAndPost(window_);
    return true;
}

bool FramebufferBridge::start_render_loop() {
    if (rendering_.load()) return true;
    if (!is_open()) {
        YPSILON_LOGE("start_render_loop: framebuffer not open");
        return false;
    }
    rendering_.store(true);

    render_thread_ = std::thread([this]() {
        YPSILON_LOGI("FramebufferBridge[%s]: render loop started", instance_id_.c_str());

        // A Looper is required on this thread so AChoreographer's internal handler can dispatch callbacks when pumped below
        ALooper_prepare(0);
        AChoreographer* choreographer = AChoreographer_getInstance();
        AChoreographer_postFrameCallback(choreographer, &FramebufferBridge::on_vsync, this);

        while (rendering_.load()) {
            // Pumps the Looper so Choreographer can fire on_vsync; the 16ms timeout only bounds shutdown latency, frame pacing comes from vsync itself
            ALooper_pollOnce(16, nullptr, nullptr, nullptr);
        }

        YPSILON_LOGI("FramebufferBridge[%s]: render loop stopped", instance_id_.c_str());
    });

    return true;
}

void FramebufferBridge::stop_render_loop() {
    if (!rendering_.load()) return;
    rendering_.store(false);
    if (render_thread_.joinable()) render_thread_.join();
}

void FramebufferBridge::on_vsync(long /*frame_time_nanos*/, void* data) {
    auto* self = static_cast<FramebufferBridge*>(data);
    if (!self->rendering_.load()) return; // Don't re-arm past shutdown.

    if (self->window_ && self->fb_mmap_) {
        self->blit_to_window();
    }

    AChoreographer_postFrameCallback(
        AChoreographer_getInstance(), &FramebufferBridge::on_vsync, data);
}

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

} // namespace ypsilon::display
