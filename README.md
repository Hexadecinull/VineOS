<div align="center">

# 🌿 VineOS

**A free, open-source, ad-free Android-on-Android virtual machine.**
Run a full isolated Android guest inside your existing device —
with **32-bit app support on arm64-only hardware** via QEMU user-mode emulation.

[![VineOS Android CI](https://github.com/Hexadecinull/VineOS/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/ci.yml)
[![VineOS CodeQL CI](https://github.com/Hexadecinull/VineOS/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/codeql.yml)
[![VineOS Compile-time Sanity Checks](https://github.com/Hexadecinull/VineOS/actions/workflows/sanity.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/sanity.yml)
[![VineOS Dependency Review](https://github.com/Hexadecinull/VineOS/actions/workflows/dependency-review.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/dependency-review.yml)
[![VineOS Nightly Build CI](https://github.com/Hexadecinull/VineOS/actions/workflows/nightly.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/nightly.yml)
[![VineOS Release CI](https://github.com/Hexadecinull/VineOS/actions/workflows/release.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/release.yml)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Hexadecinull/VineOS/total)](https://github.com/Hexadecinull/VineOS/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-%237F52FF.svg?logo=kotlin&logoColor=white)](https://github.com/Hexadecinull/VineOS/search?l=kotlin)
[![C++](https://img.shields.io/badge/C++-%2300599C.svg?logo=c%2B%2B&logoColor=white)](https://github.com/Hexadecinull/VineOS/search?l=c%2B%2B)
[![C](https://img.shields.io/badge/C-00599C?logo=c&logoColor=white)](https://github.com/Hexadecinull/VineOS/search?l=c)
[![GitHub Downloads (latest)](https://img.shields.io/github/downloads/Hexadecinull/VineOS/latest)](https://github.com/Hexadecinull/VineOS/releases/latest)
[![GitHub Release](https://img.shields.io/github/v/release/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/releases/latest)
[![GitHub Repo stars](https://img.shields.io/github/stars/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/stargazers)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/pulls)
[![GitHub Issues](https://img.shields.io/github/issues/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/issues)

[**Download**](#installation) · [**Building**](BUILDING.md) · [**Contributing**](https://github.com/Hexadecinull/VineOS?tab=contributing-ov-file) · [**Roadmap**](#roadmap) · [**Security**](SECURITY.md)

</div>

---

## Table of Contents

- [What is VineOS?](#what-is-vineos)
- [Features](#features)
- [How it works](#how-it-works)
  - [Architecture overview](#architecture-overview)
  - [Native runtime (C++)](#native-runtime-c)
  - [32-bit support on arm64-only devices](#32-bit-support-on-arm64-only-devices)
  - [Container isolation](#container-isolation)
  - [Display pipeline](#display-pipeline)
  - [Input pipeline](#input-pipeline)
- [Supported devices](#supported-devices)
- [ROM support](#rom-support)
- [Installation](#installation)
- [Building from source](#building-from-source)
- [Roadmap](#roadmap)
- [FAQ](#faq)
- [Contributing](#contributing)
- [License](#license)
- [Credits & acknowledgements](#credits--acknowledgements)

---

## What is VineOS?

VineOS runs a **complete, isolated Android operating system** inside your existing Android device — without modifying the host OS or requiring root on most paths.

Unlike simple "app cloners" (Parallel Space, Dual Space) that sandbox individual apps, VineOS virtualizes the **full Android stack**: its own `init`, `Zygote`, `SurfaceFlinger`, `ServiceManager`, and everything else. You get a real second phone inside your phone.

**Why VineOS over alternatives like VPhoneOS or VMOS?**

| Feature | VineOS | VPhoneOS | VMOS | Virtual Master |
|---|:---:|:---:|:---:|:---:|
| Open source | ✅ | ❌ | ❌ | ❌ |
| Ad-free | ✅ | ❌ | ❌ | ❌ |
| Free (fully) | ✅ | Partial | Partial | ❌ |
| 32-bit on arm64-only devices | ✅ | ✅ | ❌ | ❌ |
| GPL-3.0 licensed | ✅ | ❌ | ❌ | ❌ |
| Material You | ✅ | ❌ | ❌ | ❌ |
| Multiple ROM versions | ✅ | Paid | Partial | Paid |
| No telemetry | ✅ | ❌ | ❌ | ❌ |

---

## Features

- 🆓 **Completely free** — no paywalls, no premium tiers, no ads, ever
- 🔓 **Open source** — GPL-3.0, fully auditable
- 📦 **Multiple ROMs** — Android 7.1, 9.0, 11, 12 (more planned)
- 🏗️ **32-bit support everywhere** — runs armeabi-v7a apps on arm64-only devices via QEMU user-mode binary translation
- 🎨 **Material You** — dynamic color from your wallpaper on Android 12+, static vine-green fallback on 8–11
- ⚡ **Lightweight** — zero background overhead when no VM is running; no persistent daemons
- 🔒 **Isolated** — PID, mount, UTS, and IPC namespace separation
- 🌱 **Multi-instance** — run multiple independent VMs simultaneously
- 🔧 **Root support** — optional Magisk/root inside instances (requires rooted host)
- 📱 **minSdk 26** — supports Android 8.0+ (~97% of active devices)

---

## How it works

### Architecture overview

```
┌──────────────────────────────────────────────────────────────┐
│                     VineOS Android App                        │
│             (Kotlin + Jetpack Compose + Material3)            │
│                                                               │
│  HomeScreen ──┐                                               │
│  ROMsScreen ──┼── ViewModels ── Repositories ──┐             │
│  Settings    ─┘                                 │             │
│                                          VineVMManager        │
│                                                 │ JNI         │
├─────────────────────────────────────────────────▼────────────┤
│                  VineOS Native Runtime (C++17)                │
│                                                               │
│  vine_runtime.cpp          ← JNI entry points                 │
│                                                               │
│  ┌──────────────────┐  ┌─────────────────────────────────┐   │
│  │ NamespaceManager │  │  InstanceRuntime (per VM)        │   │
│  │ + Container      │  │  ┌──────────────────────────┐   │   │
│  │                  │  │  │  FramebufferBridge        │   │   │
│  │ - PID namespace  │  │  │  fb0 → ANativeWindow      │   │   │
│  │ - Mount namespace│  │  ├──────────────────────────┤   │   │
│  │ - UTS namespace  │  │  │  UInputBridge             │   │   │
│  │ - IPC namespace  │  │  │  MT type B touch + keys   │   │   │
│  └──────────────────┘  │  └──────────────────────────┘   │   │
│                         └─────────────────────────────────┘   │
│  ┌──────────────┐  ┌──────────────────────────────────────┐  │
│  │ QEMUBridge   │  │  vine_utils (AArch32 detection,       │  │
│  │ binfmt_misc  │  │  filesystem, process helpers)         │  │
│  └──────────────┘  └──────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────┤
│                     Host Android Kernel                        │
│          (Linux namespaces, loop devices, binfmt_misc)        │
└──────────────────────────────────────────────────────────────┘
                              ▲
         ┌────────────────────┴───────────────────────┐
         │           Guest Android Userspace           │
         │   (AOSP ROM image, e.g. Android 7.1.2)     │
         │                                             │
         │  arm64-v8a apps → run natively              │
         │  armeabi-v7a apps → qemu-arm JIT            │
         └─────────────────────────────────────────────┘
```

### Native runtime (C++)

The C++ runtime is the core of VineOS. Kotlin handles UI and orchestration; C++ handles everything that requires direct kernel access. The runtime is split into focused modules:

| Module | Files | Responsibility |
|---|---|---|
| JNI bridge | `vine_runtime.cpp` | All 18 JNI entry points; owns `InstanceRuntime` map |
| Container | `container/namespace_manager.cpp/.h` | Linux namespace lifecycle, rootfs mounting, `pivot_root`, Android init launch |
| QEMU bridge | `qemu_bridge/qemu_launcher.cpp/.h` | Binary verification, binfmt_misc ARMv7 registration |
| Display | `display/framebuffer_bridge.cpp/.h` | `/dev/graphics/fb0` → `ANativeWindow` rendering pipeline |
| Input | `input/uinput_bridge.cpp/.h` | MT type B multitouch + key events via Linux `uinput` |
| Utilities | `utils/vine_utils.cpp/.h` | AArch32 detection, filesystem helpers, process helpers |

Every VM instance has its own `InstanceRuntime` struct holding a `FramebufferBridge` and `UInputBridge` alongside its container handle. When a VM starts, both bridges are created with paths pointing into the guest rootfs (`/rootfs_mnt/dev/graphics/fb0`, `/rootfs_mnt/dev/uinput`). When a VM stops, `g_runtimes.erase(handle)` tears them both down cleanly.

### 32-bit support on arm64-only devices

Modern SoCs like **Google Tensor G3** (Pixel 8 series) and **MediaTek Dimensity 8400-Ultra** (POCO X7 Pro) have removed AArch32 execution state entirely. VineOS solves this via **QEMU user-mode emulation**:

1. A statically compiled `qemu-arm` binary (arm64-v8a) is bundled as `libqemu_arm.so`.
2. VineOS detects arm64-only SoCs using a 4-layer cascade in `host_supports_aarch32()`:
   - **L1:** `ro.product.cpu.abilist32` — empty on arm64-only SoCs
   - **L2:** `ro.product.cpu.abilist` — scans for `armeabi` entries
   - **L3:** `/proc/sys/abi/` — kernel `CONFIG_COMPAT` knobs only exist with AArch32
   - **L4:** `/proc/cpuinfo aarch32_el0` — CPU feature flag (Linux 4.7+)
3. When all layers return negative, VineOS registers qemu-arm with `binfmt_misc` using the `F` flag — which opens the interpreter fd at registration time so it works correctly after `pivot_root` changes the filesystem root.
4. 64-bit apps bypass QEMU entirely and run natively.

Performance: QEMU's TCG JIT has ~1.5–3× overhead for CPU-bound code. Simple apps feel fine; heavy 3D games will be slower.

### Container isolation

Each instance runs inside Linux namespaces:

| Namespace | Effect |
|---|---|
| `CLONE_NEWPID` | Guest init becomes PID 1; guest can't see host processes |
| `CLONE_NEWNS` | Mount namespace isolation; guest mounts don't propagate to host |
| `CLONE_NEWUTS` | Guest has its own hostname (`vine-<id>`) |
| `CLONE_NEWIPC` | Isolated System V IPC and POSIX message queues |

The guest filesystem is a loop-mounted ROM image. `pivot_root` moves the process root into the guest rootfs before `execl(/init)`.

### Display pipeline

**Status: Phase 2 (in progress)**

`FramebufferBridge` reads the guest's `/dev/graphics/fb0` virtual framebuffer device and blits frames to an Android `ANativeWindow` (backed by a Compose `SurfaceView`). The pipeline supports both RGBA_8888 (direct memcpy) and RGB_565 (inline conversion to RGBA8888, needed for Android 7.x guests). Phase 2 will complete the mmap + render loop.

### Input pipeline

**Status: Phase 2 (in progress)**

`UInputBridge` creates a virtual touchscreen inside the guest using Linux's `uinput` subsystem with the MT type B multitouch protocol. Touch events from the host's `ANativeWindow` are forwarded as `ABS_MT_SLOT` / `ABS_MT_TRACKING_ID` / `ABS_MT_POSITION_X/Y` / `BTN_TOUCH` sequences. Hardware key events (back, home, volume) are translated from Android keycodes to Linux `KEY_*` codes.

---

## Supported devices

- **Android 8.0 (API 26)** or higher as host OS
- **ARM64 (arm64-v8a)** processor
- Kernel with Linux namespaces and loop device support (standard since Android 4.4)

### 32-bit compatibility

| Hardware | 32-bit mechanism | Speed |
|---|---|---|
| Standard arm64 (Snapdragon 8xx, Dimensity 9xxx, Exynos) | Hardware AArch32 | Native |
| Google Tensor G3 (Pixel 8/8 Pro/8a) | QEMU user-mode | ~1.5–3× slower |
| MediaTek Dimensity 8400-Ultra (POCO X7 Pro) | QEMU user-mode | ~1.5–3× slower |
| Any other arm64-only SoC | QEMU user-mode (auto-detected) | ~1.5–3× slower |

---

## ROM support

| ROM | Android Version | API | 32-bit | Status |
|---|---|---|---|---|
| `vine-rom-7` | Android 7.1.2 Nougat | 25 | ✅ | 🟡 In progress |
| `vine-rom-9` | Android 9.0 Pie | 28 | ✅ | 🔴 Planned |
| `vine-rom-11` | Android 11 | 30 | ✅ | 🔴 Planned |
| `vine-rom-12` | Android 12 | 31 | ✅ | 🔴 Planned |

ROMs are built from AOSP targeting the `ranchu` virtual device board and distributed as `.vrom` archives. See [BUILDING.md](BUILDING.md) for the ROM build guide.

---

## Installation

Download the latest release APK from the [Releases page](https://github.com/Hexadecinull/VineOS/releases).

VineOS is not yet on the Play Store. First release planned at Beta stability.

---

## Building from source

See **[BUILDING.md](BUILDING.md)** for the complete guide covering environment setup, NDK configuration, QEMU cross-compilation, and AOSP ROM builds.

Quick start:

```bash
git clone https://github.com/Hexadecinull/VineOS.git
cd VineOS
./gradlew :app:assembleDebug
```

---

## Roadmap

### Phase 1 — Foundation *(current)*
- [x] Linux namespace container runtime (`unshare`, `pivot_root`, `execl`)
- [x] QEMU binfmt_misc integration for 32-bit support (4-layer AArch32 detection)
- [x] JNI bridge — 18 native methods with `FramebufferBridge` + `UInputBridge` wiring
- [x] Material You UI — Home, ROMs, Settings screens
- [x] Room DB + ViewModels + DataStore preferences
- [x] ROM downloader (manifest fetch, streaming download, SHA-256 verification)
- [ ] First bootable Android 7.1.2 ROM image

### Phase 2 — Display & Input
- [ ] Framebuffer render loop (mmap → ANativeWindow blit at 60fps)
- [ ] RGB565 → RGBA8888 conversion for Android 7.x guests
- [ ] Multi-touch forwarding (MT type B, up to 10 fingers)
- [ ] Hardware key forwarding (back, home, volume)
- [ ] Clipboard sharing host ↔ guest

### Phase 3 — Storage & ROMs
- [ ] Writable `/data` partition (overlayfs on top of read-only ROM)
- [ ] Instance snapshot / restore
- [ ] Android 9, 11, 12 ROM images
- [ ] ROM CDN + in-app download UI completion

### Phase 4 — No-root Path
- [ ] `proot` fallback for non-rooted devices
- [ ] Android Virtualization Framework (AVF) backend (Android 13+)

### Phase 5 — Polish
- [ ] Play Store + F-Droid release
- [ ] Magisk inside instances
- [ ] Per-instance VPN / network isolation
- [ ] GPU passthrough research

---

## FAQ

**Does VineOS require root?**
Root is not required for the planned no-root path (Phase 4). The current implementation requires `CAP_SYS_ADMIN` for `unshare()` and `mount()`. Root is optional for Magisk inside instances.

**Will it work on my Dimensity 8400-Ultra phone (POCO X7 Pro)?**
Yes — that's one of the primary target devices. VineOS auto-detects arm64-only SoCs and enables QEMU mode automatically.

**Why GPL-3.0 and not MIT/Apache?**
VineOS uses QEMU (GPL-2.0+) and is architecturally inspired by Anbox/Waydroid (GPL-3.0). GPL-3.0 ensures commercial forks must also open their changes.

**Can I use VineOS on x86_64 Android emulators?**
Not currently. VineOS is ARM-only.

---

## Contributing

Contributions of all kinds are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

Key areas where help is most needed:
- Building the Android 7.1.2 AOSP ROM image
- Cross-compiling QEMU user-mode as a static arm64 Android binary
- Completing Phase 2 (framebuffer render loop, uinput multitouch)
- Testing on diverse devices, especially arm64-only SoCs

---

## License

VineOS is licensed under the **GNU General Public License v3.0**.
See [LICENSE](LICENSE) for the full text.

### Third-party components

| Component | License | Use |
|---|---|---|
| [QEMU](https://www.qemu.org/) | GPL-2.0-or-later | ARMv7 user-mode emulation |
| [AOSP](https://source.android.com/) | Apache-2.0 + various | ROM images |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Apache-2.0 | UI framework |
| [Hilt](https://dagger.dev/hilt/) | Apache-2.0 | Dependency injection |
| [Room](https://developer.android.com/training/data-storage/room) | Apache-2.0 | Local database |

---

## Credits & acknowledgements

- **[Anbox](https://github.com/anbox/anbox)** — original Android-in-a-Linux-container; VineOS's container architecture is directly inspired by its design
- **[Waydroid](https://github.com/waydroid/waydroid)** — maintained successor; reference for namespace setup and binfmt_misc integration
- **[redroid](https://github.com/remote-android/redroid-doc)** — Android in Docker; reference for mount table and device node setup
- **[QEMU](https://www.qemu.org/)** — the backbone of VineOS's 32-bit emulation
- **[VPhoneOS / VPhoneGaGa](https://vphoneos.com/)** — the closed-source inspiration that VineOS aims to replace with an open alternative
- The entire **Android homebrew and modding community**

---

<div align="center">
  <sub>Made with 🌿 by <a href="https://github.com/Hexadecinull">Hexadecinull</a> and contributors</sub>
</div>
