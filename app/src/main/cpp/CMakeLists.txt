cmake_minimum_required(VERSION 3.22.1)

project("vine_runtime" VERSION 0.1.0 LANGUAGES C CXX)

# ── Compiler settings ─────────────────────────────────────────────────────────

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Treat warnings as errors in debug builds; warn-only in release
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(
        -Wall -Wextra -Wpedantic -Wshadow
        -Wno-unused-parameter   # NDK headers trigger this
        -Wno-gnu-zero-variadic-macro-arguments
        -fstack-protector-strong
        -DVINE_DEBUG=1
    )
else()
    add_compile_options(
        -Wall -Wextra
        -Wno-unused-parameter
        -O2
        -fstack-protector-strong
    )
endif()

# ── Source files ──────────────────────────────────────────────────────────────

set(VINE_SOURCES
    # JNI entry point
    vine_runtime.cpp

    # Container / namespace management
    container/namespace_manager.cpp

    # QEMU bridge (binfmt_misc registration, binary verification)
    qemu_bridge/qemu_launcher.cpp

    # Display bridge (virtual framebuffer → ANativeWindow)
    display/framebuffer_bridge.cpp

    # Input bridge (uinput virtual touchscreen + keyboard)
    input/uinput_bridge.cpp

    # Utilities (logging, filesystem, AArch32 detection)
    utils/vine_utils.cpp
)

# ── Library target ────────────────────────────────────────────────────────────

add_library(
    vine_runtime
    SHARED
    ${VINE_SOURCES}
)

target_include_directories(vine_runtime PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/container
    ${CMAKE_CURRENT_SOURCE_DIR}/qemu_bridge
    ${CMAKE_CURRENT_SOURCE_DIR}/display
    ${CMAKE_CURRENT_SOURCE_DIR}/input
    ${CMAKE_CURRENT_SOURCE_DIR}/utils
)

# ── Android system libraries ──────────────────────────────────────────────────

find_library(log-lib    log)
find_library(android-lib android)

# android/native_window.h + ANativeWindow_* live in android-lib on NDK r27+
# jnigraphics: gives us AndroidBitmap_* if we ever need it for direct bitmap access
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
    vine_runtime
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)

# ── Platform feature definitions ──────────────────────────────────────────────

if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(vine_runtime PRIVATE
        VINE_HOST_ARM64=1
        # Enables the faster AArch32 detection path (skip x86 code paths)
    )
endif()

# ── Compile-time sanity checks ────────────────────────────────────────────────
# These are checked at CMake configure time, not build time. Build-time
# checks live in vine_sanity_checks.cpp (compiled into the library and
# executed via __attribute__((constructor))).

if(ANDROID_PLATFORM_LEVEL LESS 26)
    message(FATAL_ERROR
        "VineOS requires Android API 26+ (minSdk=26). "
        "Current ANDROID_PLATFORM_LEVEL=${ANDROID_PLATFORM_LEVEL}")
endif()

if(NOT CMAKE_CXX_STANDARD EQUAL 17)
    message(FATAL_ERROR "VineOS native runtime requires C++17")
endif()

message(STATUS "VineOS native: ABI=${CMAKE_ANDROID_ARCH_ABI} "
               "API=${ANDROID_PLATFORM_LEVEL} "
               "BUILD=${CMAKE_BUILD_TYPE}")
