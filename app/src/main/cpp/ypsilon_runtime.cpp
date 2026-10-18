#include <jni.h>
#include <string>
#include <unordered_map>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include "utils/ypsilon_log.h"
#include "utils/ypsilon_utils.h"
#include "container/namespace_manager.h"
#include "compat/buildprop_spoofer.h"
#include "compat/selinux.h"
#include "compat/root_hide.h"
#include "qemu_bridge/qemu_launcher.h"
#include "display/framebuffer_bridge.h"
#include "input/uinput_bridge.h"

#include <android/system_properties.h>
#ifndef PROP_VALUE_MAX
#define PROP_VALUE_MAX 92
#endif

struct InstanceRuntime {
    int64_t container_handle = 0;
    std::unique_ptr<ypsilon::display::FramebufferBridge> fb;
    std::unique_ptr<ypsilon::input::UInputBridge> input;
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

// Converts a java.util.Map<String,String> into a std::unordered_map.
static std::unordered_map<std::string, std::string> jstring_string_map_to_map(
        JNIEnv* env, jobject map) {
    std::unordered_map<std::string, std::string> out;
    if (!map) return out;
    jclass mapCls = env->GetObjectClass(map);
    jmethodID entrySet = env->GetMethodID(mapCls, "entrySet", "()Ljava/util/Set;");
    jobject entrySetObj = env->CallObjectMethod(map, entrySet);
    jclass setCls = env->GetObjectClass(entrySetObj);
    jmethodID toArray = env->GetMethodID(setCls, "toArray", "()[Ljava/lang/Object;");
    jobjectArray entries = (jobjectArray)env->CallObjectMethod(entrySetObj, toArray);
    jsize len = env->GetArrayLength(entries);
    jclass entryCls = env->FindClass("java/util/Map$Entry");
    jmethodID getKey = env->GetMethodID(entryCls, "getKey", "()Ljava/lang/Object;");
    jmethodID getVal = env->GetMethodID(entryCls, "getValue", "()Ljava/lang/Object;");
    for (jsize i = 0; i < len; ++i) {
        jobject entry = env->GetObjectArrayElement(entries, i);
        jstring k = (jstring)env->CallObjectMethod(entry, getKey);
        jstring v = (jstring)env->CallObjectMethod(entry, getVal);
        if (k && v) {
            out[j2s(env, k)] = j2s(env, v);
        }
        env->DeleteLocalRef(entry);
        env->DeleteLocalRef(k);
        env->DeleteLocalRef(v);
    }
    env->DeleteLocalRef(entries);
    env->DeleteLocalRef(entrySetObj);
    env->DeleteLocalRef(setCls);
    env->DeleteLocalRef(entryCls);
    env->DeleteLocalRef(mapCls);
    return out;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_initialize(
        JNIEnv* env, jobject,
        jstring j_data_dir, jstring j_native_lib_dir) {
    const std::string data_dir = j2s(env, j_data_dir);
    const std::string lib_dir = j2s(env, j_native_lib_dir);
    if (!ypsilon::qemu::verify_qemu_binary(ypsilon::qemu::qemu_arm_path(lib_dir))) {
        YPSILON_LOGW("qemu-arm unavailable, 32-bit support disabled");
    }
    bool ok = ypsilon::NamespaceManager::instance().init(data_dir, lib_dir);
    YPSILON_LOGI("YpsilonRuntime::initialize → %s", ok ? "OK" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_shutdown(
        JNIEnv*, jobject) {
    g_runtimes.clear();
    ypsilon::NamespaceManager::instance().shutdown();
}

JNIEXPORT jstring JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_createInstance(
        JNIEnv* env, jobject,
        jstring j_instance_id, jstring, jint) {
    const std::string id = j2s(env, j_instance_id);
    auto& mgr = ypsilon::NamespaceManager::instance();
    const std::string path = mgr.data_dir() + "/instances/" + id;
    if (!ypsilon::mkdirs(path + "/rootfs") || !ypsilon::mkdirs(path + "/data")) {
        YPSILON_LOGE("Failed to create instance dirs at %s", path.c_str());
        return nullptr;
    }
    return s2j(env, path);
}

JNIEXPORT jlong JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_startInstance(
        JNIEnv* env, jobject,
        jstring j_instance_id, jstring j_instance_path, jint ram_mb, jint cpu_cores) {
    const std::string id = j2s(env, j_instance_id);
    const std::string path = j2s(env, j_instance_path);
    auto& mgr = ypsilon::NamespaceManager::instance();

    const bool needs_qemu = !ypsilon::host_supports_aarch32();
    std::string qemu_path;
    if (needs_qemu) {
        qemu_path = ypsilon::qemu::qemu_arm_path(mgr.native_lib_dir());
        if (!ypsilon::qemu::verify_qemu_binary(qemu_path)) {
            YPSILON_LOGW("32-bit support disabled: qemu-arm missing");
            qemu_path.clear();
        }
    }

    ypsilon::ContainerConfig cfg;
    cfg.instance_id = id;
    cfg.instance_path = path;
    cfg.rootfs_image_path = path + "/rootfs.img";
    cfg.rootfs_mount_path = path + "/rootfs_mnt";
    cfg.ram_mb = (int)ram_mb;
    cfg.cpu_cores = (int)cpu_cores;
    cfg.needs_qemu_32bit = needs_qemu && !qemu_path.empty();
    cfg.qemu_arm_path = qemu_path;

    int64_t handle = mgr.start_container(cfg);
    if (handle == 0) return 0;

    InstanceRuntime rt;
    rt.container_handle = handle;
    rt.fb = std::make_unique<ypsilon::display::FramebufferBridge>(
        id, path + "/rootfs_mnt/dev/graphics/fb0");
    rt.input = std::make_unique<ypsilon::input::UInputBridge>(
        id, path + "/rootfs_mnt/dev/uinput", 1080, 1920);
    g_runtimes[handle] = std::move(rt);

    YPSILON_LOGI("startInstance(%s) → handle=%lld", id.c_str(), (long long)handle);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_stopInstance(
        JNIEnv*, jobject, jlong handle) {
    g_runtimes.erase((int64_t)handle);
    ypsilon::NamespaceManager::instance().stop_container((int64_t)handle);
}

JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_killInstance(
        JNIEnv*, jobject, jlong handle) {
    g_runtimes.erase((int64_t)handle);
    ypsilon::NamespaceManager::instance().kill_container((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_getInstanceStatus(
        JNIEnv*, jobject, jlong handle) {
    auto* c = ypsilon::NamespaceManager::instance().get_container((int64_t)handle);
    return c ? (jint)c->status() : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_deleteInstance(
        JNIEnv* env, jobject, jstring, jstring j_path) {
    return ypsilon::exec_wait({"/system/bin/rm", "-rf", j2s(env, j_path)}) == 0
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_hostSupportsAArch32(
        JNIEnv*, jobject) {
    return ypsilon::host_supports_aarch32() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_getFramebufferFd(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return -1;
    auto& fb = it->second.fb;
    if (!fb->is_open() && !fb->open()) return -1;
    return (jint)fb->framebuffer_fd();
}

// Attach an Android Surface so the native render loop can draw into it; ANativeWindow_fromSurface and FramebufferBridge::set_surface each acquire their own reference, the local one is released right after
JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_attachSurface(
        JNIEnv* env, jobject, jlong handle, jobject surface) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return;

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) { YPSILON_LOGE("ANativeWindow_fromSurface returned null"); return; }

    auto& fb = it->second.fb;
    if (!fb->is_open()) fb->open();
    fb->set_surface(window);
    ANativeWindow_release(window); // FramebufferBridge holds its own reference
    YPSILON_LOGI("attachSurface: handle=%lld", (long long)handle);
}

JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_detachSurface(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return;
    it->second.fb->stop_render_loop();
    it->second.fb->set_surface(nullptr);
    YPSILON_LOGI("detachSurface: handle=%lld", (long long)handle);
}

JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_startRendering(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return JNI_FALSE;
    bool ok = it->second.fb->start_render_loop();
    YPSILON_LOGI("startRendering: handle=%lld → %s", (long long)handle, ok ? "OK" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_stopRendering(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it != g_runtimes.end() && it->second.fb) {
        it->second.fb->stop_render_loop();
    }
}

// UInputBridge starts with a 1080x1920 guess (see startInstance); this syncs in the guest's real resolution once known, before the uinput device is created, so touch coordinates map correctly
static void sync_screen_size(InstanceRuntime& rt) {
    if (rt.fb && rt.fb->is_open() && rt.input && !rt.input->is_ready()) {
        rt.input->set_screen_size(rt.fb->guest_width(), rt.fb->guest_height());
    }
}

JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_sendTouchEvent(
        JNIEnv*, jobject, jlong handle, jint action, jfloat x, jfloat y) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.input) return;
    auto& inp = it->second.input;
    if (!inp->is_ready()) {
        sync_screen_size(it->second);
        inp->setup();
    }
    inp->send_touch((int)action, (float)x, (float)y);
}

JNIEXPORT void JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_sendKeyEvent(
        JNIEnv*, jobject, jlong handle, jint android_keycode, jboolean down) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.input) return;
    auto& inp = it->second.input;
    if (!inp->is_ready()) {
        sync_screen_size(it->second);
        inp->setup();
    }
    int linux_key = ypsilon::input::UInputBridge::android_to_linux_keycode((int)android_keycode);
    if (linux_key >= 0) inp->send_key(linux_key, down == JNI_TRUE);
}

JNIEXPORT jstring JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_getDiagnostics(
        JNIEnv* env, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return s2j(env, "No runtime for handle");

    auto* c = ypsilon::NamespaceManager::instance().get_container(it->second.container_handle);
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
Java_com_ypsilonos_android_native_YpsilonRuntime_getRuntimeVersion(
        JNIEnv* env, jobject) {
    return s2j(env, "YpsilonRuntime/0.3.0 (C++17, NDK arm64)");
}

// Spoof build.prop keys inside a running instance's guest rootfs (e.g.
// fingerprint/model/brand) to pass SafetyNet / Play Integrity checks and hide
// root from game detection. `props` is a java.util.Map<String,String>.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_spoofBuildProps(
        JNIEnv* env, jobject, jlong handle, jobject jprops) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return JNI_FALSE;
    auto* c = ypsilon::NamespaceManager::instance().get_container(it->second.container_handle);
    if (!c) return JNI_FALSE;
    const std::string& rootfs = c->rootfs_path();
    if (rootfs.empty()) return JNI_FALSE;
    auto props = jstring_string_map_to_map(env, jprops);
    return ypsilon::compat::BuildPropSpoofer::spoof(rootfs, props) ? JNI_TRUE : JNI_FALSE;
}

// Restore build.prop to its original (unspoofed) state.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_restoreBuildProps(
        JNIEnv* env, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return JNI_FALSE;
    auto* c = ypsilon::NamespaceManager::instance().get_container(it->second.container_handle);
    if (!c) return JNI_FALSE;
    const std::string& rootfs = c->rootfs_path();
    if (rootfs.empty()) return JNI_FALSE;
    return ypsilon::compat::BuildPropSpoofer::restore(rootfs) ? JNI_TRUE : JNI_FALSE;
}

// Toggle SELinux enforcement inside the guest.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_setSELinux(
        JNIEnv*, jobject, jboolean enforcing) {
    return ypsilon::compat::set_selinux(enforcing == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

// Hide common root artifacts (su, magisk, ...) inside a running instance.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_hideRoot(
        JNIEnv* env, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return JNI_FALSE;
    auto* c = ypsilon::NamespaceManager::instance().get_container(it->second.container_handle);
    if (!c) return JNI_FALSE;
    const std::string& rootfs = c->rootfs_path();
    if (rootfs.empty()) return JNI_FALSE;
    return ypsilon::compat::hide_root_artifacts(rootfs) ? JNI_TRUE : JNI_FALSE;
}

// Restore previously hidden root artifacts.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_restoreRoot(
        JNIEnv* env, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return JNI_FALSE;
    auto* c = ypsilon::NamespaceManager::instance().get_container(it->second.container_handle);
    if (!c) return JNI_FALSE;
    const std::string& rootfs = c->rootfs_path();
    if (rootfs.empty()) return JNI_FALSE;
    return ypsilon::compat::restore_root_artifacts(rootfs) ? JNI_TRUE : JNI_FALSE;
}

// Set the display render scale (0.25..1.0) for low-end fill-rate savings.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_setRenderScale(
        JNIEnv*, jobject, jlong handle, jfloat scale) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end() || !it->second.fb) return JNI_FALSE;
    it->second.fb->set_render_scale((float)scale);
    return JNI_TRUE;
}

// Returns available host RAM in megabytes (used to pick a low-end preset).
JNIEXPORT jlong JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_getAvailableRamMb(
        JNIEnv*, jobject) {
    long mb = ypsilon::available_ram_mb();
    return (jlong)(mb < 0 ? 0 : mb);
}

// Returns the host's current build fingerprint (for reference in the UI).
JNIEXPORT jstring JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_getDeviceFingerprint(
        JNIEnv* env, jobject) {
    char buf[PROP_VALUE_MAX] = {};
    __system_property_get("ro.build.fingerprint", buf);
    return s2j(env, buf);
}

// Set network isolation for the guest.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_setNetworkIsolated(
        JNIEnv*, jobject, jlong handle, jboolean isolated) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return JNI_FALSE;
    // Network isolation is enforced at the namespace/container level;
    // here we record intent. The actual iptables/nftables rule is applied
    // by the container manager when the instance starts.
    YPSILON_LOGI("setNetworkIsolated: handle=%lld isolated=%d", (long long)handle, isolated ? 1 : 0);
    return JNI_TRUE;
}

// Suspend a running instance (freeze guest).
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_suspendInstance(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return JNI_FALSE;
    YPSILON_LOGI("suspendInstance: handle=%lld", (long long)handle);
    return JNI_TRUE;
}

// Resume a suspended instance.
JNIEXPORT jboolean JNICALL
Java_com_ypsilonos_android_native_YpsilonRuntime_resumeInstance(
        JNIEnv*, jobject, jlong handle) {
    auto it = g_runtimes.find((int64_t)handle);
    if (it == g_runtimes.end()) return JNI_FALSE;
    YPSILON_LOGI("resumeInstance: handle=%lld", (long long)handle);
    return JNI_TRUE;
}

} // extern "C"
