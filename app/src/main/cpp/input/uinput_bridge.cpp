#include "uinput_bridge.h"
#include "../utils/vine_log.h"

#include <cerrno>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/uinput.h>
#include <linux/input-event-codes.h>

namespace vine::input {

// ─── Constructor / Destructor ─────────────────────────────────────────────────

UInputBridge::UInputBridge(
        std::string instance_id,
        std::string uinput_dev_path,
        int screen_width,
        int screen_height)
    : instance_id_(std::move(instance_id))
    , uinput_dev_path_(std::move(uinput_dev_path))
    , screen_width_(screen_width)
    , screen_height_(screen_height) {}

UInputBridge::~UInputBridge() {
    teardown();
}

// ─── setup() ─────────────────────────────────────────────────────────────────

bool UInputBridge::setup() {
    VINE_LOGI("UInputBridge[%s]: setting up virtual touchscreen (%dx%d)",
              instance_id_.c_str(), screen_width_, screen_height_);

    uinput_fd_ = open(uinput_dev_path_.c_str(), O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (uinput_fd_ < 0) {
        VINE_LOGE_ERRNO(("open(" + uinput_dev_path_ + ")").c_str());
        return false;
    }

    // ── Enable event types ────────────────────────────────────────────────────
    // EV_KEY: needed for BTN_TOUCH (touchscreen press)
    if (ioctl(uinput_fd_, UI_SET_EVBIT, EV_KEY) < 0 ||
        ioctl(uinput_fd_, UI_SET_KEYBIT, BTN_TOUCH) < 0) {
        VINE_LOGE_ERRNO("UI_SET_EVBIT/KEYBIT for BTN_TOUCH");
        teardown();
        return false;
    }

    // EV_ABS: absolute position events (required for touchscreens)
    if (ioctl(uinput_fd_, UI_SET_EVBIT, EV_ABS) < 0) {
        VINE_LOGE_ERRNO("UI_SET_EVBIT EV_ABS");
        teardown();
        return false;
    }

    // ── Configure MT type B absolute axes ────────────────────────────────────
    // MT type B protocol (slot-based) is what Android 4.x+ InputReader expects.
    // We declare: ABS_MT_SLOT, ABS_MT_TRACKING_ID, ABS_MT_POSITION_X/Y,
    //             ABS_MT_PRESSURE, ABS_MT_TOUCH_MAJOR
    // Non-MT axes: ABS_X, ABS_Y for single-touch compatibility layer.

    struct AbsAxis {
        uint16_t code;
        int min, max, fuzz, flat, resolution;
    };

    const AbsAxis axes[] = {
        { ABS_MT_SLOT,         0,  9,         0, 0, 0   }, // up to 10 fingers
        { ABS_MT_TRACKING_ID,  0,  65535,     0, 0, 0   }, // unique ID per contact
        { ABS_MT_POSITION_X,   0,  screen_width_  - 1, 0, 0, 0 },
        { ABS_MT_POSITION_Y,   0,  screen_height_ - 1, 0, 0, 0 },
        { ABS_MT_PRESSURE,     0,  255,       0, 0, 0   },
        { ABS_MT_TOUCH_MAJOR,  0,  255,       0, 0, 0   },
        { ABS_X,               0,  screen_width_  - 1, 0, 0, 0 },
        { ABS_Y,               0,  screen_height_ - 1, 0, 0, 0 },
    };

    for (const auto& axis : axes) {
        if (ioctl(uinput_fd_, UI_SET_ABSBIT, axis.code) < 0) {
            VINE_LOGE("UI_SET_ABSBIT(%d) failed: %s", axis.code, strerror(errno));
            teardown();
            return false;
        }
    }

    // ── Create the virtual device ─────────────────────────────────────────────
    struct uinput_setup usetup{};
    strncpy(usetup.name, "vine-touchscreen", UINPUT_MAX_NAME_SIZE - 1);
    usetup.id.bustype = BUS_VIRTUAL;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x5678;
    usetup.id.version = 1;

    if (ioctl(uinput_fd_, UI_DEV_SETUP, &usetup) < 0) {
        VINE_LOGE_ERRNO("UI_DEV_SETUP");
        teardown();
        return false;
    }

    // Set absolute axis parameters (min/max/fuzz/flat/resolution)
    for (const auto& axis : axes) {
        struct uinput_abs_setup abs_setup{};
        abs_setup.code              = axis.code;
        abs_setup.absinfo.minimum   = axis.min;
        abs_setup.absinfo.maximum   = axis.max;
        abs_setup.absinfo.fuzz      = axis.fuzz;
        abs_setup.absinfo.flat      = axis.flat;
        abs_setup.absinfo.resolution = axis.resolution;
        if (ioctl(uinput_fd_, UI_ABS_SETUP, &abs_setup) < 0) {
            VINE_LOGW("UI_ABS_SETUP(%d) failed: %s (non-fatal)", axis.code, strerror(errno));
        }
    }

    if (ioctl(uinput_fd_, UI_DEV_CREATE) < 0) {
        VINE_LOGE_ERRNO("UI_DEV_CREATE");
        teardown();
        return false;
    }

    device_created_ = true;
    VINE_LOGI("UInputBridge[%s]: virtual touchscreen created", instance_id_.c_str());
    return true;
}

// ─── teardown() ───────────────────────────────────────────────────────────────

void UInputBridge::teardown() {
    if (device_created_ && uinput_fd_ >= 0) {
        ioctl(uinput_fd_, UI_DEV_DESTROY);
        device_created_ = false;
    }
    if (uinput_fd_ >= 0) {
        close(uinput_fd_);
        uinput_fd_ = -1;
    }
    VINE_LOGD("UInputBridge[%s]: torn down", instance_id_.c_str());
}

// ─── send_touch() ─────────────────────────────────────────────────────────────
//
// MT type B protocol sequence for a single touch DOWN:
//   EV_ABS ABS_MT_SLOT        0
//   EV_ABS ABS_MT_TRACKING_ID <new unique id>
//   EV_ABS ABS_MT_POSITION_X  <x>
//   EV_ABS ABS_MT_POSITION_Y  <y>
//   EV_ABS ABS_MT_PRESSURE    128
//   EV_KEY BTN_TOUCH          1
//   EV_SYN SYN_REPORT         0
//
// For MOVE: same as above but no TRACKING_ID change.
//
// For UP:
//   EV_ABS ABS_MT_SLOT        0
//   EV_ABS ABS_MT_TRACKING_ID -1  (releases the slot)
//   EV_KEY BTN_TOUCH          0
//   EV_SYN SYN_REPORT         0

void UInputBridge::send_touch(int action, float x, float y) {
    if (!is_ready()) return;

    const int ix = static_cast<int>(x);
    const int iy = static_cast<int>(y);

    switch (action) {
        case 0: // DOWN
            write_event(EV_ABS, ABS_MT_SLOT,         0);
            write_event(EV_ABS, ABS_MT_TRACKING_ID,  ++active_slot_);
            write_event(EV_ABS, ABS_MT_POSITION_X,   ix);
            write_event(EV_ABS, ABS_MT_POSITION_Y,   iy);
            write_event(EV_ABS, ABS_MT_PRESSURE,     128);
            write_event(EV_ABS, ABS_X,               ix);
            write_event(EV_ABS, ABS_Y,               iy);
            write_event(EV_KEY, BTN_TOUCH,           1);
            break;

        case 1: // MOVE
            write_event(EV_ABS, ABS_MT_SLOT,         0);
            write_event(EV_ABS, ABS_MT_POSITION_X,   ix);
            write_event(EV_ABS, ABS_MT_POSITION_Y,   iy);
            write_event(EV_ABS, ABS_MT_PRESSURE,     128);
            write_event(EV_ABS, ABS_X,               ix);
            write_event(EV_ABS, ABS_Y,               iy);
            break;

        case 2: // UP
            write_event(EV_ABS, ABS_MT_SLOT,         0);
            write_event(EV_ABS, ABS_MT_TRACKING_ID,  -1); // Release slot
            write_event(EV_KEY, BTN_TOUCH,           0);
            break;

        default:
            VINE_LOGW("send_touch: unknown action %d", action);
            return;
    }

    sync();
}

void UInputBridge::send_key(int linux_keycode, bool down) {
    if (!is_ready()) return;
    write_event(EV_KEY, static_cast<uint16_t>(linux_keycode), down ? 1 : 0);
    sync();
}

// ─── android_to_linux_keycode() ──────────────────────────────────────────────

int UInputBridge::android_to_linux_keycode(int android_keycode) {
    // Android KEYCODE → Linux KEY_ mapping for the most common navigation/hardware keys.
    // Source: Android InputManagerService + linux/input-event-codes.h
    // Android keycodes: https://developer.android.com/reference/android/view/KeyEvent
    switch (android_keycode) {
        case 3:   return KEY_HOME;          // KEYCODE_HOME
        case 4:   return KEY_BACK;          // KEYCODE_BACK
        case 6:   return KEY_PLAYPAUSE;     // KEYCODE_CALL / KEYCODE_ENDCALL (repurposed)
        case 24:  return KEY_VOLUMEUP;      // KEYCODE_VOLUME_UP
        case 25:  return KEY_VOLUMEDOWN;    // KEYCODE_VOLUME_DOWN
        case 26:  return KEY_POWER;         // KEYCODE_POWER
        case 27:  return KEY_CAMERA;        // KEYCODE_CAMERA
        case 62:  return KEY_SPACE;         // KEYCODE_SPACE
        case 66:  return KEY_ENTER;         // KEYCODE_ENTER
        case 67:  return KEY_BACKSPACE;     // KEYCODE_DEL
        case 82:  return KEY_MENU;          // KEYCODE_MENU
        case 164: return KEY_MUTE;          // KEYCODE_VOLUME_MUTE
        case 187: return KEY_PAGEUP;        // KEYCODE_APP_SWITCH (recents)
        default:
            // For regular alphanumeric keys: Android KEYCODE_A=29, Linux KEY_A=30
            // Android offset is -1 relative to Linux for the A-Z range.
            if (android_keycode >= 29 && android_keycode <= 54) {
                return android_keycode + 1;
            }
            // Digit keys: Android KEYCODE_0=7..KEYCODE_9=16, Linux KEY_0=11..KEY_9=2
            // (non-trivial, skip for now)
            VINE_LOGD("android_to_linux_keycode: no mapping for Android keycode %d",
                      android_keycode);
            return -1;
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

bool UInputBridge::write_event(uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev{};
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    // gettimeofday(&ev.time, nullptr); // Kernel fills this in for uinput
    ssize_t written = write(uinput_fd_, &ev, sizeof(ev));
    if (written != sizeof(ev)) {
        VINE_LOGW("write_event(%d,%d,%d): wrote %zd/%zu bytes",
                  type, code, value, written, sizeof(ev));
        return false;
    }
    return true;
}

void UInputBridge::sync() {
    write_event(EV_SYN, SYN_REPORT, 0);
}

} // namespace vine::input
