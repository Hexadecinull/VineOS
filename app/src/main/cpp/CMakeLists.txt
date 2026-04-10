cmake_minimum_required(VERSION 3.22.1)

project("vine_runtime" VERSION 0.1.0 LANGUAGES C CXX)

# ── Compiler settings ─────────────────────────────────────────────────────────

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Strict warnings
add_compile_options(
    -Wall
    -Wextra
    -Wshadow
    -Wno-unused-parameter   # Android NDK headers trigger this a lot
    -fstack-protector-strong
)

# ── Source files ──────────────────────────────────────────────────────────────

set(VINE_SOURCES
    # Main JNI entry point
    vine_runtime.cpp

    # Container / namespace management
    container/namespace_manager.cpp

    # QEMU bridge (binfmt_misc registration, qemu-arm launcher)
    qemu_bridge/qemu_launcher.cpp

    # Utilities
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
    ${CMAKE_CURRENT_SOURCE_DIR}/utils
)

# ── Android system libraries ──────────────────────────────────────────────────

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    vine_runtime
    ${log-lib}
    ${android-lib}
)

# ── Feature detection ─────────────────────────────────────────────────────────

# Detect if this is an arm64-only build (no AArch32 support in the toolchain,
# which hints at target devices that may have dropped AArch32 entirely).
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(vine_runtime PRIVATE VINE_HOST_ARM64=1)
endif()

# Debug build: enable verbose logging from native side
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_definitions(vine_runtime PRIVATE VINE_DEBUG=1)
endif()
