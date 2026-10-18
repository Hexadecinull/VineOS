#pragma once

#include <android/log.h>

// Usage: YPSILON_LOGI("Starting container for instance %s", instanceId);

#define YPSILON_LOG_TAG "YpsilonRuntime"

#define YPSILON_LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, YPSILON_LOG_TAG, __VA_ARGS__)
#define YPSILON_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,   YPSILON_LOG_TAG, __VA_ARGS__)
#define YPSILON_LOGI(...) __android_log_print(ANDROID_LOG_INFO,    YPSILON_LOG_TAG, __VA_ARGS__)
#define YPSILON_LOGW(...) __android_log_print(ANDROID_LOG_WARN,    YPSILON_LOG_TAG, __VA_ARGS__)
#define YPSILON_LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   YPSILON_LOG_TAG, __VA_ARGS__)

// Log errno automatically on syscall failure
#define YPSILON_LOGE_ERRNO(msg) \
    YPSILON_LOGE("%s failed at %s:%d: %s", msg, __FILE__, __LINE__, strerror(errno))

// Conditional debug logging (compiled out in release builds)
#ifdef YPSILON_DEBUG
#define YPSILON_LOGD_FUNC() YPSILON_LOGD("→ %s()", __FUNCTION__)
#else
#define YPSILON_LOGD_FUNC() do {} while(0)
#endif
