# 🌿 VineOS

**A free, open-source, ad-free Android-on-Android virtual machine with full 32-bit support on arm64-only devices.**

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%203.0-green.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-blue.svg)]()
[![Target SDK](https://img.shields.io/badge/targetSdk-36%20(Android%2016)-brightgreen.svg)]()
[![Language](https://img.shields.io/badge/Language-Kotlin%20%2B%20C%2FC%2B%2B-orange.svg)]()

---

## What is VineOS?

VineOS runs a complete, isolated Android guest OS inside your existing Android device — no root required on most paths. Unlike simple cloning apps, it virtualizes the full Android stack. Unlike closed-source competitors (VPhoneOS, VMOS, Virtual Master), it is entirely open-source, ad-free, and designed to be lightweight.

**Key differentiators:**
- ✅ 32-bit (armeabi-v7a) support on arm64-only devices (Pixel 8 series, Dimensity 8400-Ultra, Tensor G3, etc.)
- ✅ Multiple ROM versions (Android 7 → 12+), downloadable in-app
- ✅ Material You (dynamic theming) with graceful fallback to M3 on older Android
- ✅ Fully open source, GPL-3.0
- ✅ Zero ads, zero telemetry, zero paywalls
- ✅ Lightweight: no background daemons when no VM is running

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  VineOS Android App                      │
│         (Kotlin + Jetpack Compose + Material3)           │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │   Instances  │  │     ROMs     │  │   Settings    │  │
│  │   (HomeScreen)│  │ (ROMsScreen) │  │(SettingsScreen)│ │
│  └──────┬───────┘  └──────┬───────┘  └───────────────┘  │
│         └─────────────────┼──────────────────────────┘   │
│                    ┌──────▼───────┐                       │
│                    │VineVMManager │  (Domain layer)        │
│                    └──────┬───────┘                       │
│                           │ JNI                           │
├───────────────────────────▼─────────────────────────────┤
│                  VineOS Native Runtime (C/C++)            │
│                                                          │
│  ┌─────────────────────┐   ┌──────────────────────────┐  │
│  │  NamespaceManager   │   │      QEMUBridge          │  │
│  │  - PID namespace    │   │  - qemu-arm (static,     │  │
│  │  - Mount namespace  │   │    arm64 binary)         │  │
│  │  - Network namespace│   │  - binfmt_misc setup     │  │
│  │  - User namespace   │   │  - ARMv7 → AArch64 JIT  │  │
│  └─────────────────────┘   └──────────────────────────┘  │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐  │
│  │               ContainerRuntime                       │  │
│  │  - Guest rootfs mount (loop device / bind mount)    │  │
│  │  - Android init bootstrap                           │  │
│  │  - Zygote lifecycle management                      │  │
│  │  - Display bridge (framebuffer → SurfaceView)       │  │
│  └─────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│                  Host Android Kernel                      │
│     (Linux namespaces, binfmt_misc, loop devices)        │
└─────────────────────────────────────────────────────────┘
         ▲
         │  Guest runs here ──────────────────────────────┐
         │                                                 │
         │  ┌─────────────────────────────────────────┐   │
         └──│         Guest Android Userspace          │   │
            │  (AOSP-based ROM image, e.g. Android 7)  │   │
            │                                          │   │
            │  arm64-v8a apps: run natively            │   │
            │  armeabi-v7a apps: → qemu-arm (JIT)     │   │
            └─────────────────────────────────────────┘   │
                                                           │
```

---

## How 32-bit Support Works on arm64-Only Devices

On normal ARM64 devices, the CPU supports both AArch64 and AArch32 execution states. On newer devices (Google Tensor G3, MediaTek Dimensity 8400-Ultra), AArch32 is removed from the CPU entirely. VineOS solves this by:

1. Bundling a **statically compiled `qemu-arm` binary** (ARMv7 userspace emulator, compiled as an arm64 Android executable).
2. Inside the guest namespace, registering it with **`binfmt_misc`**:
   ```
   /proc/sys/fs/binfmt_misc/register ← ARMv7 ELF magic → /system/bin/qemu-arm
   ```
3. Every time the guest's Zygote spawns a 32-bit process, the kernel transparently routes it through QEMU's **dynamic binary translator (DBT)**, which JIT-compiles ARMv7 → AArch64 instruction blocks on the fly.
4. 64-bit apps bypass QEMU entirely — they execute natively.

Performance: QEMU DBT has ~1.5–3× overhead vs native for CPU-bound workloads. Simple apps (social media, utilities) will feel fine. Heavy 3D games will be slow. This is the same trade-off VPhoneOS makes.

---

## 32-Bit Support Matrix

| Device Type | Mechanism | Speed |
|---|---|---|
| arm64 + AArch32 hardware support (most devices) | Hardware AArch32 mode (no emulation) | Native |
| arm64-only (Tensor G3, Dimensity 8400-Ultra) | QEMU user-mode DBT | ~1.5–3× slower |
| x86_64 (emulators) | Not supported (ARM-only) | N/A |

---

## ROM Support Roadmap

| ROM | Android Version | Status |
|---|---|---|
| `vine-rom-7` | Android 7.1.2 | 🟡 Planned (Phase 1 target) |
| `vine-rom-9` | Android 9.0 | 🔴 Future |
| `vine-rom-11` | Android 11 | 🔴 Future |
| `vine-rom-12` | Android 12 | 🔴 Future |

ROMs are built from AOSP sources targeting the `ranchu` virtual device board, stripped to minimum viable system, and distributed as compressed `.vrom` image bundles.

---

## Project Structure

```
VineOS/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/hexadecinull/vineos/
│       │   ├── MainActivity.kt          ← Entry point, Compose host
│       │   ├── ui/
│       │   │   ├── theme/               ← Material You theming
│       │   │   ├── navigation/          ← Nav graph
│       │   │   ├── screens/             ← HomeScreen, ROMsScreen, SettingsScreen
│       │   │   └── components/          ← Reusable composables
│       │   ├── data/
│       │   │   ├── models/              ← VMInstance, ROMImage data classes
│       │   │   └── repository/          ← InstanceRepository, ROMRepository
│       │   ├── domain/
│       │   │   └── VineVMManager.kt     ← VM lifecycle orchestrator
│       │   ├── service/
│       │   │   └── VineService.kt       ← Foreground service for running VMs
│       │   └── native/
│       │       └── VineRuntime.kt       ← JNI bridge to C++ runtime
│       └── cpp/
│           ├── CMakeLists.txt
│           ├── vine_runtime.cpp         ← Main native entry point
│           ├── container/
│           │   ├── namespace_manager.cpp
│           │   └── namespace_manager.h
│           ├── qemu_bridge/
│           │   ├── qemu_launcher.cpp
│           │   └── qemu_launcher.h
│           └── utils/
│               ├── vine_log.h
│               └── vine_utils.cpp
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Build Requirements

- Android Studio Ladybug (2024.2) or newer
- NDK r27 or newer
- CMake 3.22+
- Min API 26 / Target API 36

## License

GPL-3.0 — see [LICENSE](LICENSE)
