cmake_minimum_required(VERSION 3.22.1)

project("vine_runtime" VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(
        -Wall -Wextra -Wpedantic -Wshadow
        -Wno-unused-parameter
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

set(VINE_SOURCES
    vine_runtime.cpp
    container/namespace_manager.cpp
    qemu_bridge/qemu_launcher.cpp
    display/framebuffer_bridge.cpp
    input/uinput_bridge.cpp
    utils/vine_utils.cpp
    diagnostics/namespace_probe.cpp
)

add_library(vine_runtime SHARED ${VINE_SOURCES})

target_include_directories(vine_runtime PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/container
    ${CMAKE_CURRENT_SOURCE_DIR}/qemu_bridge
    ${CMAKE_CURRENT_SOURCE_DIR}/display
    ${CMAKE_CURRENT_SOURCE_DIR}/input
    ${CMAKE_CURRENT_SOURCE_DIR}/utils
    ${CMAKE_CURRENT_SOURCE_DIR}/diagnostics
)

find_library(log-lib     log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(vine_runtime
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)

if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(vine_runtime PRIVATE VINE_HOST_ARM64=1)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    target_compile_definitions(vine_runtime PRIVATE VINE_HOST_ARM32=1)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    target_compile_definitions(vine_runtime PRIVATE VINE_HOST_X86_64=1)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    target_compile_definitions(vine_runtime PRIVATE VINE_HOST_X86=1)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi")
    target_compile_definitions(vine_runtime PRIVATE VINE_HOST_ARMEABI=1)
endif()

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
