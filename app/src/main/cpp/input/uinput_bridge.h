#pragma once

#include <string>
#include <functional>
#include <sys/types.h>

namespace vine::input {

struct TouchPoint {
    int slot;   // Multitouch slot index (0-9)
    float x;
    float y;
    bool active; // true = finger down, false = finger lifted
};

enum class TouchAction { DOWN = 0, MOVE = 1, UP = 2 };

// Creates a virtual touchscreen inside the guest via the Linux uinput
// subsystem, registered as a multitouch type B device (what Android 4.x+
// expects). Host touch/key events come in through VineRuntime's JNI layer,
// get written as input_event structs to /dev/uinput, and the guest's
// InputReader picks them up like a real hardware device.
class UInputBridge {
public:
    static constexpr int kMaxSlots = 10;

    // uinput_dev_path is /dev/uinput inside the guest rootfs. screen_width
    // and screen_height set the MT coordinate range.
    UInputBridge(
        std::string instance_id,
        std::string uinput_dev_path,
        int screen_width,
        int screen_height);
    ~UInputBridge();

    UInputBridge(const UInputBridge&) = delete;
    UInputBridge& operator=(const UInputBridge&) = delete;

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

    // Updates the MT coordinate range used by setup(). Only takes effect
    // before setup() has run; a no-op once the device is already created.
    void set_screen_size(int width, int height);

    // action: 0=DOWN, 1=MOVE, 2=UP. x/y are in guest screen space.
    void send_touch(int action, float x, float y);

    // Up to kMaxSlots concurrent contacts. Each point's slot must stay
    // consistent across calls for a given finger (DOWN then MOVE... then
    // an inactive point to lift it), same as Android's own MT protocol.
    void send_multitouch(const TouchPoint* points, int count);

    // linux_keycode is a Linux KEY_* code from linux/input-event-codes.h.
    void send_key(int linux_keycode, bool down);

    // Maps an Android KEYCODE_* to the equivalent Linux KEY_*, or -1.
    static int android_to_linux_keycode(int android_keycode);

private:
    std::string instance_id_;
    std::string uinput_dev_path_;
    int screen_width_;
    int screen_height_;

    int uinput_fd_ = -1;
    bool device_created_ = false;
    int active_slot_ = 0; // Current single-touch slot

    // Per-slot tracking ID for send_multitouch, -1 means the slot is free.
    int slot_tracking_ids_[kMaxSlots];
    int next_tracking_id_ = 1;

    bool write_event(uint16_t type, uint16_t code, int32_t value);
    void sync(); // Writes a SYN_REPORT to flush the current event batch.
};

} // namespace vine::input
