#pragma once

#include <string>
#include <memory>
#include <functional>
#include <atomic>
#include <thread>
#include <android/native_window.h>

namespace vine::display {

enum class FrameFormat {
    RGBA_8888,
    RGBX_8888,
    RGB_565,
    BGRA_8888,
};

class FramebufferBridge {
public:
    using ResizeCallback = std::function<void(int width, int height)>;

    FramebufferBridge(std::string instance_id, std::string fb_device_path);
    ~FramebufferBridge();

    FramebufferBridge(const FramebufferBridge&) = delete;
    FramebufferBridge& operator=(const FramebufferBridge&) = delete;

    // Lifecycle
    bool open();
    void close();
    void set_surface(ANativeWindow* window);

    // Rendering
    bool start_render_loop();
    void stop_render_loop();
    bool render_frame();

    // Info
    int guest_width() const { return guest_width_; }
    int guest_height() const { return guest_height_; }
    int framebuffer_fd() const { return fb_fd_; }
    FrameFormat format() const { return format_; }
    bool is_open() const { return fb_fd_ >= 0 && fb_mmap_ != nullptr; }
    bool is_rendering() const { return rendering_.load(); }

    void set_resize_callback(ResizeCallback cb) { resize_cb_ = std::move(cb); }

    static void convert_rgb565_to_rgba8888(
        const void* src, void* dst,
        int width, int height, int src_stride);

private:
    std::string instance_id_;
    std::string fb_device_path_;

    int fb_fd_ = -1;
    ANativeWindow* window_ = nullptr;

    int guest_width_ = 0;
    int guest_height_ = 0;
    int guest_stride_ = 0;
    FrameFormat format_ = FrameFormat::RGBA_8888;
    size_t frame_size_ = 0;
    void* fb_mmap_ = nullptr;

    std::atomic<bool> rendering_{false};
    std::thread render_thread_;

    ResizeCallback resize_cb_;

    bool query_fb_geometry();
    bool mmap_fb();
    void munmap_fb();
    bool blit_to_window();
};

} // namespace vine::display
