#include <jni.h>
#include <string>
#include <unordered_map>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include "utils/vine_log.h"
#include "utils/vine_utils.h"
#include "container/namespace_manager.h"
#include "qemu_bridge/qemu_launcher.h"
#include "display/framebuffer_bridge.h"
#include "input/uinput_bridge.h"

struct InstanceRuntime {
    int64_t container_handle = 0;
    std::unique_ptr<vine::display::FramebufferBridge> fb;
    std::unique_ptr<vine::input::UInputBridge> input;
};

static std::unordered_map<int64_t, InstanceRuntime> g_runtimes;

static std::string j2s(JNIEnv* env, jstring js) {
    if (!js) return {};
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

static jstring s2j(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_initialize(
        JNIEnv* env, jobject,
        jstring j_data_dir, jstring j_native_lib_dir) {
    const std::string data_dir = j2s(env, j_data_dir);
    const std::string lib_dir = j2s(env, j_native_lib_dir);
    if (!vine::qemu::verify_qemu_binary(vine::qemu::qemu_arm_path(lib_dir))) {
        VINE_LOGW("qemu-arm unavailable — 32-bit support disabled");
    }
    bool ok = vine::NamespaceManager::instance().init(data_dir, lib_dir);
    VINE_LOGI("VineRuntime::initialize → %s", ok ? "OK" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_shutdown(
        JNIEnv*, jobject) {
    g_runtimes.clear();
    vine::NamespaceManager::instance().shutdown();
}

JNIEXPORT jstring JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_createInstance(
        JNIEnv* env, jobject,
        jstring j_instance_id, jstring, jint) {
    const std::string id = j2s(env, j_instance_id);
    auto& mgr = vine::NamespaceManager::instance();
    const std::string path = mgr.data_dir() + "/instances/" + id;
    if (!vine::mkdirs(path + "/rootfs") || !vine::mkdirs(path + "/data")) {
        VINE_LOGE("Failed to create instance dirs at %s", path.c_str());
        return nullptr;
    }
    return s2j(env, path);
}

JNIEXPORT jlong JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_startInstance(
        JNIEnv* env, jobject,
        jstring j_instance_id, jstring j_instance_path, jint ram_mb) {
    const std::string id = j2s(env, j_instance_id);
    const std::string path = j2s(env, j_instance_path);
    auto& mgr = vine::NamespaceManager::instance();

    const bool needs_qemu = !vine::host_supports_aarch32();
    std::string qemu_path;
    if (needs_qemu) {
        qemu_path = vine::qemu::qemu_arm_path(mgr.native_lib_dir());
        if (!vine::qemu::verify_qemu_binary(qemu_path)) {
            VINE_LOGW("32-bit support disabled: qemu-arm missing");
            qemu_path.clear();
        }
    }

    vine::ContainerConfig cfg;
    cfg.instance_id = id;
    cfg.instance_path = path;
    cfg.rootfs_image_path = path + "/rootfs.img";
    cfg.rootfs_mount_path = path + "/rootfs_mnt";
    cfg.ram_mb = (int)ram_mb;
    cfg.needs_qemu_32bit = needs_qemu && !qemu_path.empty();
    cfg.qemu_arm_path = qemu_path;

    int64_t handle = mgr.start_container(cfg);
    if (handle == 0) return 0;

    InstanceRuntime rt;
    rt.container_handle = handle;
    rt.fb = std::make_unique<vine::display::FramebufferBridge>(
        id, path + "/rootfs_mnt/dev/graphics/fb0");
    rt.input = std::make_unique<vine::input::UInputBridge>(
        id, path + "/rootfs_mnt/dev/uinput", 1080, 1920);
    g_runtimes[handle] = std::move(rt);

    VINE_LOGI("startInstance(%s) → handle=%lld", id.c_str(), (long long)handle);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_stopInstance(
        JNIEnv*, jobject, jlong handle) {
    g_runtimes.erase((int64_t)handle);
    vine::NamespaceManager::instance().stop_container((int64_t)handle);
}

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_killInstance(
        JNIEnv*, jobject, jlong handle) {
    g_runtimes.erase((int64_t)handle);
    vine::NamespaceManager::instance().kill_container((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getInstanceStatus(
        JNIEnv*, jobject, jlong handle) {
    auto* c = vine::NamespaceManager::instance().get_container((int64_t)handle);
    return c ? (jint)c->status() : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_deleteInstance(
        JNIEnv* env, jobject, jstring, jstring j_path) {
    return vine::exec_wait({"/system/bin/rm", "-rf", j2s(env, j_path)}) == 0
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_hostSupportsAArch32(
        JNIEnv*, jobject) {
    return vine::host_supports_aarch32() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_registerQemuBinfmt(
        JNIEnv*, jobject, jlong, jstring) {
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getFramebufferFd(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return -1;
    auto& fb = it->second.fb;
    if (!fb->is_open() && !fb->open()) return -1;
    return (jint)fb->framebuffer_fd();
}

// Attach an Android Surface so the native render loop can draw into it.
// ANativeWindow_fromSurface() acquires a reference; FramebufferBridge::set_surface()
// acquires another. We release the local ref immediately after passing it in.
JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_attachSurface(
        JNIEnv* env, jobject, jlong handle, jobject surface) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return;

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) { VINE_LOGE("ANativeWindow_fromSurface returned null"); return; }

    auto& fb = it->second.fb;
    if (!fb->is_open()) fb->open();
    fb->set_surface(window);
    ANativeWindow_release(window); // FramebufferBridge holds its own reference
    VINE_LOGI("attachSurface: handle=%lld", (long long)handle);
}

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_detachSurface(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return;
    it->second.fb->stop_render_loop();
    it->second.fb->set_surface(nullptr);
    VINE_LOGI("detachSurface: handle=%lld", (long long)handle);
}

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_startRendering(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return JNI_FALSE;
    bool ok = it->second.fb->start_render_loop();
    VINE_LOGI("startRendering: handle=%lld → %s", (long long)handle, ok ? "OK" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_stopRendering(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it != g_runtimes.end() && it->second.fb) {
        it->second.fb->stop_render_loop();
    }
}

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_sendTouchEvent(
        JNIEnv*, jobject, jlong handle, jint action, jfloat x, jfloat y) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.input) return;
    auto& inp = it->second.input;
    if (!inp->is_ready()) inp->setup();
    inp->send_touch((int)action, (float)x, (float)y);
}

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_sendKeyEvent(
        JNIEnv*, jobject, jlong handle, jint android_keycode, jboolean down) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.input) return;
    auto& inp = it->second.input;
    if (!inp->is_ready()) inp->setup();
    int linux_key = vine::input::UInputBridge::android_to_linux_keycode((int)android_keycode);
    if (linux_key >= 0) inp->send_key(linux_key, down == JNI_TRUE);
}

JNIEXPORT jstring JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getDiagnostics(
        JNIEnv* env, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return s2j(env, "No runtime for handle");

    auto* c = vine::NamespaceManager::instance().get_container(it->second.container_handle);
    std::string diag = c ? c->diagnostics() : "Container not found";

    if (it->second.fb) {
        diag += "\n--- Display ---\n";
        diag += "open: " + std::string(it->second.fb->is_open() ? "yes" : "no") + "\n";
        diag += "rendering: " + std::string(it->second.fb->is_rendering() ? "yes" : "no") + "\n";
        diag += "resolution: " + std::to_string(it->second.fb->guest_width()) + "x"
              + std::to_string(it->second.fb->guest_height()) + "\n";
    }
    if (it->second.input) {
        diag += "\n--- Input ---\n";
        diag += "uinput ready: " + std::string(it->second.input->is_ready() ? "yes" : "no") + "\n";
    }
    return s2j(env, diag);
}

JNIEXPORT jstring JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getRuntimeVersion(
        JNIEnv* env, jobject) {
    return s2j(env, "VineRuntime/0.1.0-alpha (C++17, NDK arm64)");
}

} // extern "C"
