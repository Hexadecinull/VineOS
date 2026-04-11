#pragma once

#include <string>
#include <functional>
#include <sys/types.h>

namespace vine::input {

// ─── TouchEvent ──────────────────────────────────────────────────────────────

struct TouchPoint {
    int slot;   // Multitouch slot index (0-9)
    float x;
    float y;
    bool active; // true = finger down, false = finger lifted
};

enum class TouchAction { DOWN = 0, MOVE = 1, UP = 2 };

// ─── UInputBridge ────────────────────────────────────────────────────────────

/**
 * UInputBridge — creates a virtual input device inside the guest container
 * using the Linux uinput subsystem, and forwards touch + key events from the
 * host (Android) into the guest.
 *
 * Architecture:
 *   Host ANativeWindow touch events (MotionEvent from Kotlin)
 *       ↓ JNI via VineRuntime.sendTouchEvent()
 *       ↓ UInputBridge::send_touch()
 *       ↓ writes Linux input_event structs to /dev/uinput fd
 *       ↓ Kernel's uinput driver synthesizes an input device visible to the guest
 *       ↓ Guest Android's InputReader receives the events
 *       ↓ Guest app gets MotionEvents
 *
 * The virtual device is registered as a multitouch (MT) type B device, which
 * is the protocol Android 4.x+ expects from touchscreens.
 *
 * Why not VNC or framebuffer cursor approach?
 *   uinput is the correct kernel-native approach. The guest sees a real hardware
 *   input device, which means all of Android's touch event processing
 *   (gesture recognition, pressure, palm rejection etc.) works correctly.
 *
 * Phase 2 TODO:
 *   - Complete setup() with full MT type B protocol configuration
 *   - Implement multitouch slot tracking (up to 10 concurrent fingers)
 *   - Add pressure and contact size reporting
 */
class UInputBridge {
public:
    /**
     * @param instance_id      VM instance this bridge belongs to
     * @param uinput_dev_path  Path to /dev/uinput inside the guest rootfs
     * @param screen_width     Guest display width (for MT coordinate range)
     * @param screen_height    Guest display height
     */
    UInputBridge(
        std::string instance_id,
        std::string uinput_dev_path,
        int screen_width,
        int screen_height);
    ~UInputBridge();

    UInputBridge(const UInputBridge&) = delete;
    UInputBridge& operator=(const UInputBridge&) = delete;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Open /dev/uinput and register the virtual touchscreen device.
     * Returns true on success.
     */
    bool setup();

    /**
     * Destroy the virtual input device and close the uinput fd.
     */
    void teardown();

    bool is_ready() const { return uinput_fd_ >= 0 && device_created_; }

    // ── Event injection ───────────────────────────────────────────────────────

    /**
     * Send a single-touch event. Translates host MotionEvent actions to
     * Linux MT type B protocol events.
     *
     * @param action  0=DOWN, 1=MOVE, 2=UP
     * @param x       X coordinate in guest screen space [0, screen_width-1]
     * @param y       Y coordinate in guest screen space [0, screen_height-1]
     */
    void send_touch(int action, float x, float y);

    /**
     * Send a multitouch event (future: support up to 10 concurrent touch points).
     */
    void send_multitouch(const TouchPoint* points, int count);

    /**
     * Send a key event (back, home, volume, power, etc.).
     *
     * @param linux_keycode  Linux KEY_* code (from linux/input-event-codes.h)
     * @param down           true=key pressed, false=key released
     */
    void send_key(int linux_keycode, bool down);

    // ── Android keycode translation ───────────────────────────────────────────

    /**
     * Translate an Android KeyEvent keycode (KEYCODE_*) to the equivalent
     * Linux input_event key code (KEY_*).
     *
     * Returns -1 if no mapping exists.
     */
    static int android_to_linux_keycode(int android_keycode);

private:
    std::string instance_id_;
    std::string uinput_dev_path_;
    int screen_width_;
    int screen_height_;

    int uinput_fd_ = -1;
    bool device_created_ = false;
    int active_slot_ = 0;       // Current single-touch slot

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Write a raw input_event to the uinput file descriptor.
     */
    bool write_event(uint16_t type, uint16_t code, int32_t value);

    /**
     * Write a SYN_REPORT event to flush the current event batch.
     */
    void sync();
};

} // namespace vine::input
