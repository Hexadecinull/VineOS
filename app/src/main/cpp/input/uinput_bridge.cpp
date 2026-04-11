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

UInputBridge::UInputBridge(
        std::string instance_id, std::string uinput_dev_path,
        int screen_width, int screen_height)
    : instance_id_(std::move(instance_id))
    , uinput_dev_path_(std::move(uinput_dev_path))
    , screen_width_(screen_width)
    , screen_height_(screen_height) {}

UInputBridge::~UInputBridge() { teardown(); }

bool UInputBridge::setup() {
    uinput_fd_ = open(uinput_dev_path_.c_str(), O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (uinput_fd_ < 0) { VINE_LOGE_ERRNO(("open(" + uinput_dev_path_ + ")").c_str()); return false; }

    if (ioctl(uinput_fd_, UI_SET_EVBIT, EV_KEY) < 0 ||
        ioctl(uinput_fd_, UI_SET_KEYBIT, BTN_TOUCH) < 0 ||
        ioctl(uinput_fd_, UI_SET_EVBIT, EV_ABS) < 0) {
        VINE_LOGE_ERRNO("uinput EV setup"); teardown(); return false;
    }

    struct AbsAxis { uint16_t code; int min, max; };
    const AbsAxis axes[] = {
        { ABS_MT_SLOT,         0, 9 },
        { ABS_MT_TRACKING_ID,  0, 65535 },
        { ABS_MT_POSITION_X,   0, screen_width_  - 1 },
        { ABS_MT_POSITION_Y,   0, screen_height_ - 1 },
        { ABS_MT_PRESSURE,     0, 255 },
        { ABS_MT_TOUCH_MAJOR,  0, 255 },
        { ABS_X,               0, screen_width_  - 1 },
        { ABS_Y,               0, screen_height_ - 1 },
    };
    for (const auto& a : axes) {
        if (ioctl(uinput_fd_, UI_SET_ABSBIT, a.code) < 0) {
            VINE_LOGE("UI_SET_ABSBIT(%d): %s", a.code, strerror(errno));
            teardown(); return false;
        }
    }

    struct uinput_setup usetup{};
    strncpy(usetup.name, "vine-touchscreen", UINPUT_MAX_NAME_SIZE - 1);
    usetup.id.bustype = BUS_VIRTUAL;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x5678;
    usetup.id.version = 1;
    if (ioctl(uinput_fd_, UI_DEV_SETUP, &usetup) < 0) {
        VINE_LOGE_ERRNO("UI_DEV_SETUP"); teardown(); return false;
    }

    for (const auto& a : axes) {
        struct uinput_abs_setup abs{};
        abs.code = a.code;
        abs.absinfo.minimum = a.min;
        abs.absinfo.maximum = a.max;
        ioctl(uinput_fd_, UI_ABS_SETUP, &abs);
    }

    if (ioctl(uinput_fd_, UI_DEV_CREATE) < 0) {
        VINE_LOGE_ERRNO("UI_DEV_CREATE"); teardown(); return false;
    }

    device_created_ = true;
    VINE_LOGI("UInputBridge[%s]: virtual touchscreen created (%dx%d)",
              instance_id_.c_str(), screen_width_, screen_height_);
    return true;
}

void UInputBridge::teardown() {
    if (device_created_ && uinput_fd_ >= 0) { ioctl(uinput_fd_, UI_DEV_DESTROY); device_created_ = false; }
    if (uinput_fd_ >= 0) { close(uinput_fd_); uinput_fd_ = -1; }
}

// MT type B protocol: slot → tracking ID → position → pressure → BTN_TOUCH → SYN
void UInputBridge::send_touch(int action, float x, float y) {
    if (!is_ready()) return;
    const int ix = (int)x, iy = (int)y;
    switch (action) {
        case 0: // DOWN
            write_event(EV_ABS, ABS_MT_SLOT,        0);
            write_event(EV_ABS, ABS_MT_TRACKING_ID, ++active_slot_);
            write_event(EV_ABS, ABS_MT_POSITION_X,  ix);
            write_event(EV_ABS, ABS_MT_POSITION_Y,  iy);
            write_event(EV_ABS, ABS_MT_PRESSURE,    128);
            write_event(EV_ABS, ABS_X,              ix);
            write_event(EV_ABS, ABS_Y,              iy);
            write_event(EV_KEY, BTN_TOUCH,          1);
            break;
        case 1: // MOVE
            write_event(EV_ABS, ABS_MT_SLOT,        0);
            write_event(EV_ABS, ABS_MT_POSITION_X,  ix);
            write_event(EV_ABS, ABS_MT_POSITION_Y,  iy);
            write_event(EV_ABS, ABS_MT_PRESSURE,    128);
            write_event(EV_ABS, ABS_X,              ix);
            write_event(EV_ABS, ABS_Y,              iy);
            break;
        case 2: // UP — TRACKING_ID = -1 releases the slot
            write_event(EV_ABS, ABS_MT_SLOT,        0);
            write_event(EV_ABS, ABS_MT_TRACKING_ID, -1);
            write_event(EV_KEY, BTN_TOUCH,          0);
            break;
        default:
            VINE_LOGW("send_touch: unknown action %d", action);
            return;
    }
    sync();
}

void UInputBridge::send_key(int linux_keycode, bool down) {
    if (!is_ready()) return;
    write_event(EV_KEY, (uint16_t)linux_keycode, down ? 1 : 0);
    sync();
}

int UInputBridge::android_to_linux_keycode(int android_keycode) {
    switch (android_keycode) {
        case 3:   return KEY_HOME;
        case 4:   return KEY_BACK;
        case 24:  return KEY_VOLUMEUP;
        case 25:  return KEY_VOLUMEDOWN;
        case 26:  return KEY_POWER;
        case 27:  return KEY_CAMERA;
        case 62:  return KEY_SPACE;
        case 66:  return KEY_ENTER;
        case 67:  return KEY_BACKSPACE;
        case 82:  return KEY_MENU;
        case 164: return KEY_MUTE;
        default:
            if (android_keycode >= 29 && android_keycode <= 54) return android_keycode + 1;
            return -1;
    }
}

bool UInputBridge::write_event(uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev{};
    ev.type = type; ev.code = code; ev.value = value;
    return write(uinput_fd_, &ev, sizeof(ev)) == (ssize_t)sizeof(ev);
}

void UInputBridge::sync() { write_event(EV_SYN, SYN_REPORT, 0); }

} // namespace vine::input
