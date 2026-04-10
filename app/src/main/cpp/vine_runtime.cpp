#include <jni.h>
#include <string>

#include "utils/vine_log.h"
#include "utils/vine_utils.h"
#include "container/namespace_manager.h"
#include "qemu_bridge/qemu_launcher.h"

// ─── JNI Helpers ──────────────────────────────────────────────────────────────

static std::string jstring_to_std(JNIEnv* env, jstring js) {
    if (!js) return {};
    const char* chars = env->GetStringUTFChars(js, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(js, chars);
    return result;
}

static jstring std_to_jstring(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

// ─── JNI Exports ─────────────────────────────────────────────────────────────
// All functions are in the com.hexadecinull.vineos.native.VineRuntime companion
// object namespace.

extern "C" {

// ── initialize() ──────────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_initialize(
        JNIEnv* env, jobject /* this */,
        jstring j_data_dir,
        jstring j_native_lib_dir) {

    VINE_LOGI("VineRuntime.initialize()");
    std::string data_dir = jstring_to_std(env, j_data_dir);
    std::string native_lib_dir = jstring_to_std(env, j_native_lib_dir);

    // Verify QEMU binary exists and is usable (arm64-v8a static binary)
    std::string qemu_path = vine::qemu::qemu_arm_path(native_lib_dir);
    bool qemu_ok = vine::qemu::verify_qemu_binary(qemu_path);
    if (!qemu_ok) {
        VINE_LOGW("qemu-arm binary not available or invalid — 32-bit support will be disabled");
        // Non-fatal: the runtime still initializes, but 32-bit instances won't be created
    }

    bool ok = vine::NamespaceManager::instance().init(data_dir, native_lib_dir);
    VINE_LOGI("VineRuntime.initialize() → %s", ok ? "OK" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ── shutdown() ────────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_shutdown(
        JNIEnv* /* env */, jobject /* this */) {
    VINE_LOGI("VineRuntime.shutdown()");
    vine::NamespaceManager::instance().shutdown();
}

// ── createInstance() ─────────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_createInstance(
        JNIEnv* env, jobject /* this */,
        jstring j_instance_id,
        jstring j_rom_image_path,
        jint storage_mb) {

    std::string instance_id = jstring_to_std(env, j_instance_id);
    std::string rom_image_path = jstring_to_std(env, j_rom_image_path);
    VINE_LOGI("VineRuntime.createInstance(%s, %s, %dMB)",
              instance_id.c_str(), rom_image_path.c_str(), (int)storage_mb);

    auto& mgr = vine::NamespaceManager::instance();
    std::string instance_path = mgr.data_dir() + "/instances/" + instance_id;

    // Create directory structure for this instance
    if (!vine::mkdirs(instance_path + "/rootfs")) {
        VINE_LOGE("Failed to create instance directory: %s", instance_path.c_str());
        return nullptr;
    }
    if (!vine::mkdirs(instance_path + "/data")) {
        VINE_LOGE("Failed to create instance data directory");
        return nullptr;
    }

    // TODO Phase 2: Create a sparse /data partition image of storage_mb size
    // and copy the ROM's /data skeleton into it. For now we just return the path.
    // fallocate(2) or dd can create a sparse file quickly.

    VINE_LOGI("Instance directory created: %s", instance_path.c_str());
    return std_to_jstring(env, instance_path);
}

// ── startInstance() ──────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_startInstance(
        JNIEnv* env, jobject /* this */,
        jstring j_instance_id,
        jstring j_instance_path,
        jint ram_mb) {

    std::string instance_id = jstring_to_std(env, j_instance_id);
    std::string instance_path = jstring_to_std(env, j_instance_path);
    VINE_LOGI("VineRuntime.startInstance(%s)", instance_id.c_str());

    auto& mgr = vine::NamespaceManager::instance();

    bool needs_qemu = !vine::host_supports_aarch32();
    std::string qemu_path;
    if (needs_qemu) {
        qemu_path = vine::qemu::qemu_arm_path(mgr.native_lib_dir());
        if (!vine::qemu::verify_qemu_binary(qemu_path)) {
            VINE_LOGW("32-bit support unavailable: qemu-arm missing or invalid");
            needs_qemu = false;
        } else {
            VINE_LOGI("Host lacks AArch32 — enabling QEMU user-mode for this instance");
        }
    }

    vine::ContainerConfig cfg;
    cfg.instance_id = instance_id;
    cfg.instance_path = instance_path;
    // ROM image: a compressed/raw Android filesystem image bundled with VineOS
    // For Phase 1 we expect it at instance_path/rootfs.img
    cfg.rootfs_image_path = instance_path + "/rootfs.img";
    cfg.rootfs_mount_path = instance_path + "/rootfs_mnt";
    cfg.ram_mb = (int)ram_mb;
    cfg.needs_qemu_32bit = needs_qemu;
    cfg.qemu_arm_path = qemu_path;

    int64_t handle = mgr.start_container(cfg);
    VINE_LOGI("startInstance(%s) → handle=%lld", instance_id.c_str(), (long long)handle);
    return (jlong)handle;
}

// ── stopInstance() ───────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_stopInstance(
        JNIEnv* /* env */, jobject /* this */,
        jlong handle) {
    VINE_LOGI("VineRuntime.stopInstance(handle=%lld)", (long long)handle);
    vine::NamespaceManager::instance().stop_container((int64_t)handle);
}

// ── killInstance() ────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_killInstance(
        JNIEnv* /* env */, jobject /* this */,
        jlong handle) {
    VINE_LOGI("VineRuntime.killInstance(handle=%lld)", (long long)handle);
    vine::NamespaceManager::instance().kill_container((int64_t)handle);
}

// ── getInstanceStatus() ───────────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getInstanceStatus(
        JNIEnv* /* env */, jobject /* this */,
        jlong handle) {
    auto* container = vine::NamespaceManager::instance().get_container((int64_t)handle);
    if (!container) return 0; // STOPPED
    return (jint)container->status();
}

// ── deleteInstance() ─────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_deleteInstance(
        JNIEnv* env, jobject /* this */,
        jstring j_instance_id,
        jstring j_instance_path) {

    std::string instance_id = jstring_to_std(env, j_instance_id);
    std::string instance_path = jstring_to_std(env, j_instance_path);
    VINE_LOGI("VineRuntime.deleteInstance(%s, %s)", instance_id.c_str(), instance_path.c_str());

    // Delegate to shell rm -rf for simplicity (exec_wait handles this safely)
    int rc = vine::exec_wait({"/system/bin/rm", "-rf", instance_path});
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

// ── hostSupportsAArch32() ─────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_hostSupportsAArch32(
        JNIEnv* /* env */, jobject /* this */) {
    return vine::host_supports_aarch32() ? JNI_TRUE : JNI_FALSE;
}

// ── registerQemuBinfmt() ──────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_registerQemuBinfmt(
        JNIEnv* env, jobject /* this */,
        jlong handle,
        jstring j_qemu_arm_path) {

    std::string qemu_path = jstring_to_std(env, j_qemu_arm_path);
    auto* container = vine::NamespaceManager::instance().get_container((int64_t)handle);
    if (!container) {
        VINE_LOGE("registerQemuBinfmt: invalid handle %lld", (long long)handle);
        return JNI_FALSE;
    }
    // This is normally called automatically in start(); expose it for testing/manual use.
    // We'd need to make setup_binfmt_misc() public or add a friend here.
    // For now this is a no-op stub — the method is called from startInstance() internally.
    VINE_LOGW("registerQemuBinfmt: binfmt_misc is set up automatically by startInstance()");
    return JNI_TRUE;
}

// ── getFramebufferFd() ───────────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getFramebufferFd(
        JNIEnv* /* env */, jobject /* this */,
        jlong handle) {
    auto* container = vine::NamespaceManager::instance().get_container((int64_t)handle);
    if (!container) return -1;
    return (jint)container->framebuffer_fd();
}

// ── sendTouchEvent() ─────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_sendTouchEvent(
        JNIEnv* /* env */, jobject /* this */,
        jlong handle, jint action, jfloat x, jfloat y) {
    auto* container = vine::NamespaceManager::instance().get_container((int64_t)handle);
    if (container) container->send_touch((int)action, (float)x, (float)y);
}

// ── sendKeyEvent() ────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_sendKeyEvent(
        JNIEnv* /* env */, jobject /* this */,
        jlong handle, jint keycode, jboolean down) {
    auto* container = vine::NamespaceManager::instance().get_container((int64_t)handle);
    if (container) container->send_key((int)keycode, down == JNI_TRUE);
}

// ── getDiagnostics() ─────────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getDiagnostics(
        JNIEnv* env, jobject /* this */,
        jlong handle) {
    auto* container = vine::NamespaceManager::instance().get_container((int64_t)handle);
    if (!container) return std_to_jstring(env, "No container for handle");
    return std_to_jstring(env, container->diagnostics());
}

// ── getRuntimeVersion() ──────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_hexadecinull_vineos_native_VineRuntime_getRuntimeVersion(
        JNIEnv* env, jobject /* this */) {
    return std_to_jstring(env, "VineRuntime/0.1.0-alpha (C++17, NDK arm64)");
}

} // extern "C"
