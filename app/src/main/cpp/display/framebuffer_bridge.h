#pragma once

#include <string>
#include <memory>
#include <functional>
#include <android/native_window.h>

namespace vine::display {

// ─── FrameFormat ─────────────────────────────────────────────────────────────

enum class FrameFormat {
    RGBA_8888,   // Android default
    RGBX_8888,
    RGB_565,     // Common in older Android ROMs (7.x)
    BGRA_8888,   // Some guest framebuffer implementations use BGRA
};

// ─── FramebufferBridge ────────────────────────────────────────────────────────

/**
 * FramebufferBridge — reads the guest's virtual framebuffer and renders it
 * into an Android ANativeWindow (backed by a Compose SurfaceView).
 *
 * Architecture:
 *   Guest Android → writes pixels to /dev/graphics/fb0 (virtual framebuffer)
 *   FramebufferBridge → reads /dev/graphics/fb0 continuously
 *                     → pushes frames to ANativeWindow via mmap + blit
 *
 * The framebuffer device (/dev/graphics/fb0) is a virtual device that we
 * create inside the container's /dev using a tmpfs-backed virtual file.
 * The guest's SurfaceFlinger writes to it; we read from it on the host side.
 *
 * Phase 2 TODO:
 *   - Implement the render loop on a dedicated thread
 *   - Add VSync-aligned rendering for smooth 60fps output
 *   - Add hardware-accelerated blit when GPU formats match (RGB565 → RGBA8888)
 *   - Add screen rotation support (guest rotation → host transform)
 */
class FramebufferBridge {
public:
    /**
     * Callback invoked when the guest's display size changes.
     * Parameters: width, height in pixels.
     */
    using ResizeCallback = std::function<void(int width, int height)>;

    /**
     * Construct a bridge for the given instance.
     *
     * @param instance_id   The VM instance this bridge belongs to.
     * @param fb_device_path Path to the virtual framebuffer device inside
     *                       the guest rootfs (e.g. /path/to/rootfs/dev/graphics/fb0)
     */
    FramebufferBridge(std::string instance_id, std::string fb_device_path);
    ~FramebufferBridge();

    FramebufferBridge(const FramebufferBridge&) = delete;
    FramebufferBridge& operator=(const FramebufferBridge&) = delete;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Open the virtual framebuffer device and query its geometry.
     * Returns true on success.
     */
    bool open();

    /**
     * Close the framebuffer device and stop rendering.
     */
    void close();

    /**
     * Attach an ANativeWindow surface for output rendering.
     * Call this when the host SurfaceView's surface becomes available.
     * Pass nullptr to detach (when surface is destroyed).
     */
    void set_surface(ANativeWindow* window);

    // ── Rendering ────────────────────────────────────────────────────────────

    /**
     * Start the render loop on a background thread.
     * Continuously reads the framebuffer and blits to the attached surface.
     */
    bool start_render_loop();

    /**
     * Stop the render loop and join the render thread.
     */
    void stop_render_loop();

    /**
     * Render a single frame synchronously (for testing/debugging).
     * Returns true if a frame was successfully blitted.
     */
    bool render_frame();

    // ── Info ─────────────────────────────────────────────────────────────────

    int guest_width()  const { return guest_width_;  }
    int guest_height() const { return guest_height_; }
    FrameFormat format() const { return format_; }
    bool is_open() const { return fb_fd_ >= 0; }
    bool is_rendering() const { return rendering_; }

    void set_resize_callback(ResizeCallback cb) { resize_cb_ = std::move(cb); }

private:
    std::string instance_id_;
    std::string fb_device_path_;

    int fb_fd_ = -1;
    ANativeWindow* window_ = nullptr;

    int guest_width_ = 0;
    int guest_height_ = 0;
    int guest_stride_ = 0;          // bytes per row
    FrameFormat format_ = FrameFormat::RGBA_8888;
    size_t frame_size_ = 0;         // total framebuffer size in bytes
    void* fb_mmap_ = nullptr;       // mmap of the framebuffer device

    bool rendering_ = false;
    ResizeCallback resize_cb_;

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Query framebuffer geometry via FBIOGET_VSCREENINFO / FBIOGET_FSCREENINFO.
     */
    bool query_fb_geometry();

    /**
     * mmap the framebuffer device for zero-copy reading.
     */
    bool mmap_fb();
    void munmap_fb();

    /**
     * Blit one frame from the mmap'd framebuffer to the ANativeWindow.
     * Handles format conversion (e.g. RGB565 → RGBA8888) if needed.
     */
    bool blit_to_window();

    /**
     * Convert RGB565 → RGBA8888 inline.
     * Used when the guest framebuffer format is RGB565 (common in Android 7.x).
     */
    static void convert_rgb565_to_rgba8888(
        const void* src, void* dst,
        int width, int height, int src_stride);
};

} // namespace vine::display
