#pragma once

#include <android/log.h>

// ─── VineOS Native Logging ────────────────────────────────────────────────────
//
// Usage:
//   VINE_LOGI("Starting container for instance %s", instanceId);
//   VINE_LOGE("mount() failed: %s", strerror(errno));

#define VINE_LOG_TAG "VineRuntime"

#define VINE_LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, VINE_LOG_TAG, __VA_ARGS__)
#define VINE_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,   VINE_LOG_TAG, __VA_ARGS__)
#define VINE_LOGI(...) __android_log_print(ANDROID_LOG_INFO,    VINE_LOG_TAG, __VA_ARGS__)
#define VINE_LOGW(...) __android_log_print(ANDROID_LOG_WARN,    VINE_LOG_TAG, __VA_ARGS__)
#define VINE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   VINE_LOG_TAG, __VA_ARGS__)

// Log errno automatically on syscall failure
#define VINE_LOGE_ERRNO(msg) \
    VINE_LOGE("%s failed at %s:%d: %s", msg, __FILE__, __LINE__, strerror(errno))

// Conditional debug logging (compiled out in release builds)
#ifdef VINE_DEBUG
#define VINE_LOGD_FUNC() VINE_LOGD("→ %s()", __FUNCTION__)
#else
#define VINE_LOGD_FUNC() do {} while(0)
#endif
