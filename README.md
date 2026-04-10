<div align="center">

# 🌿 VineOS

**A free, open-source, ad-free Android-on-Android virtual machine.**  
Run a full isolated Android guest inside your existing device —  
with **32-bit app support on arm64-only hardware** via QEMU user-mode emulation.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%203.0-green.svg)](LICENSE)
[![Build Status](https://img.shields.io/github/actions/workflow/status/Hexadecinull/VineOS/ci.yml?branch=main&label=CI)](https://github.com/Hexadecinull/VineOS/actions)
[![Release](https://img.shields.io/github/v/release/Hexadecinull/VineOS?include_prereleases&label=latest)](https://github.com/Hexadecinull/VineOS/releases)
[![Min SDK](https://img.shields.io/badge/minSdk-26%20%28Android%208.0%29-blue)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/targetSdk-36%20%28Android%2016%29-brightgreen)](https://developer.android.com/about/versions/16)
[![Language](https://img.shields.io/badge/language-Kotlin%20%2B%20C%2FC%2B%2B-orange)](https://kotlinlang.org/)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[**Download**](#installation) · [**Documentation**](#documentation) · [**Contributing**](CONTRIBUTING.md) · [**Building**](BUILDING.md) · [**Roadmap**](#roadmap)

</div>

---

## Table of Contents

- [What is VineOS?](#what-is-vineos)
- [Features](#features)
- [Screenshots](#screenshots)
- [How it works](#how-it-works)
  - [Architecture overview](#architecture-overview)
  - [32-bit support on arm64-only devices](#32-bit-support-on-arm64-only-devices)
  - [Container isolation](#container-isolation)
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

Unlike simple "app cloners" (Parallel Space, Dual Space, etc.) that just sandbox individual apps, VineOS virtualizes the **full Android stack**: its own `init`, `Zygote`, `SurfaceFlinger`, `ServiceManager`, and everything else. You get a real second phone inside your phone.

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
- 🏗️ **32-bit support everywhere** — runs armeabi-v7a apps on arm64-only devices (Tensor G3, Dimensity 8400-Ultra, etc.) via QEMU user-mode binary translation
- 🎨 **Material You** — dynamic color extracted from your wallpaper on Android 12+, graceful static fallback on 8–11
- ⚡ **Lightweight** — zero background overhead when no VM is running; no persistent daemons
- 🔒 **Isolated** — PID, mount, UTS, and IPC namespace separation; guest cannot access host data
- 🌱 **Multi-instance** — run multiple independent VMs side-by-side
- 🔧 **Root support** — optional Magisk/root inside individual instances (requires rooted host)
- 📱 **minSdk 26** — supports Android 8.0+ (covers ~97% of active devices)

---

## Screenshots

> *(Screenshots will be added once the UI reaches a stable visual state.)*

| Instances | ROMs | Settings |
|---|---|---|
| *(coming soon)* | *(coming soon)* | *(coming soon)* |

---

## How it works

### Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│                    VineOS Android App                        │
│            (Kotlin + Jetpack Compose + Material3)            │
│                                                              │
│   HomeScreen ──┐                                             │
│   ROMsScreen ──┤── VineVMManager ── VineRuntime (JNI) ──┐   │
│   Settings   ──┘       ↕                                 │   │
│                   VineService (Foreground)                │   │
├──────────────────────────────────────────────────────────┼───┤
│                  Native Runtime (C++17, NDK)              │   │
│                                                           ↓   │
│  ┌─────────────────────┐   ┌──────────────────────────┐      │
│  │  NamespaceManager   │   │       QEMUBridge          │      │
│  │  - PID namespace    │   │  - libqemu_arm.so         │      │
│  │  - Mount namespace  │   │    (static arm64 binary)  │      │
│  │  - UTS namespace    │   │  - binfmt_misc setup      │      │
│  │  - IPC namespace    │   │  - ARMv7→AArch64 JIT      │      │
│  └─────────────────────┘   └──────────────────────────┘      │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                  ContainerRuntime                        │  │
│  │  loop-mount rootfs → bind-mount /proc /sys /dev        │  │
│  │  unshare() → pivot_root() → execl(/init)               │  │
│  │  virtual framebuffer → display bridge → SurfaceView    │  │
│  └────────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────┤
│                    Host Android Kernel                        │
│         (Linux namespaces, loop devices, binfmt_misc)        │
└──────────────────────────────────────────────────────────────┘
                                ▲
            ┌───────────────────┴──────────────────────┐
            │          Guest Android Userspace          │
            │   (AOSP ROM image, e.g. Android 7.1.2)   │
            │                                           │
            │  arm64-v8a binaries → run natively        │
            │  armeabi-v7a binaries → qemu-arm (JIT)   │
            └───────────────────────────────────────────┘
```

### 32-bit support on arm64-only devices

Modern SoCs like **Google Tensor G3** (Pixel 8 series) and **MediaTek Dimensity 8400-Ultra** (POCO X7 Pro, Redmi K80 series) have removed AArch32 execution state from the CPU entirely. No kernel patching or system-level trick can make them run 32-bit ARM code natively.

VineOS solves this through **QEMU user-mode emulation**:

1. A **statically compiled `qemu-arm` binary** (itself an arm64-v8a executable) is bundled inside the APK as `libqemu_arm.so`.
2. When VineOS detects that the host CPU lacks AArch32 support (by checking `/proc/cpuinfo` for the `aarch32_el0` feature, then falling back to `ro.product.cpu.abilist`), it automatically enables QEMU mode for new instances.
3. Inside the guest namespace, VineOS registers `qemu-arm` with the Linux kernel's **`binfmt_misc`** facility using the `F` flag:
   ```
   :arm:M::\x7fELF\x01\x01\x01...\x02\x00\x28\x00:...mask...:/path/to/qemu-arm:F
   ```
   The `F` flag is critical — it tells the kernel to open the interpreter binary at registration time, so it works correctly inside a `chroot`/`pivot_root` namespace.
4. From that point on, **every ARMv7 ELF the guest tries to execute is transparently routed through QEMU's dynamic binary translator**, which JIT-compiles ARMv7 → AArch64 instruction blocks at runtime.
5. **64-bit (arm64-v8a) apps bypass QEMU entirely** and execute natively at full speed.

**Performance trade-off:** QEMU DBT has approximately 1.5–3× CPU overhead for compute-bound workloads. Simple apps (social media, browsers, utilities) feel fine. Heavy 3D games will be noticeably slower — unavoidable given the hardware constraint.

### Container isolation

Each VineOS instance is isolated using **Linux namespaces**:

| Namespace | Purpose |
|---|---|
| `CLONE_NEWPID` | Guest init becomes PID 1; guest processes can't see host PIDs |
| `CLONE_NEWNS` | Mount namespace isolation; guest mounts don't propagate to host |
| `CLONE_NEWUTS` | Guest has its own hostname (`vine-<id>`) |
| `CLONE_NEWIPC` | Isolated System V IPC and POSIX message queues |

The guest filesystem is provided by a **loop-mounted ROM image** (ext4 or squashfs). A `pivot_root` syscall moves the process tree root into the guest rootfs. Essential virtual filesystems (`/proc`, `/sys`, `/dev`, `/dev/pts`) are bind-mounted from the host.

---

## Supported devices

VineOS requires:
- **Android 8.0 (API 26)** or higher as the host OS
- An **ARM64 (arm64-v8a)** processor
- A kernel with Linux namespaces and loop device support (standard since Android 4.4)

### 32-bit compatibility matrix

| Hardware | 32-bit mechanism | Speed |
|---|---|---|
| Standard arm64 (Snapdragon 8xx, Dimensity 9xxx, Exynos, etc.) | Hardware AArch32 | Native |
| Google Tensor G3 (Pixel 8/8 Pro/8a) | QEMU user-mode | ~1.5–3× slower |
| MediaTek Dimensity 8400-Ultra (POCO X7 Pro, etc.) | QEMU user-mode | ~1.5–3× slower |
| Any other arm64-only SoC | QEMU user-mode (auto-detected) | ~1.5–3× slower |

---

## ROM support

| ROM | Android Version | API | 32-bit | Status |
|---|---|---|---|---|
| `vine-rom-7` | Android 7.1.2 Nougat | 25 | ✅ | 🟡 In progress |
| `vine-rom-9` | Android 9.0 Pie | 28 | ✅ | 🔴 Planned |
| `vine-rom-11` | Android 11 | 30 | ✅ | 🔴 Planned |
| `vine-rom-12` | Android 12 | 31 | ✅ | 🔴 Planned |

Community-contributed ROMs are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Installation

### Pre-built APK

Download the latest release from the [Releases page](https://github.com/Hexadecinull/VineOS/releases).

> VineOS is not yet on the Play Store. First Play Store release is planned at Beta stability.

### Building from source

See **[BUILDING.md](BUILDING.md)**.

---

## Roadmap

### Phase 1 — Foundation *(current)*
- [x] Project scaffold (Kotlin + C++ NDK + Compose + Material You)
- [x] Linux namespace container runtime (`unshare`, `pivot_root`, `execl`)
- [x] QEMU binfmt_misc integration for 32-bit support
- [x] Full JNI bridge (`VineRuntime` — 18 methods)
- [x] UI: Home, ROMs, Settings screens with Material You
- [ ] Room DB + ViewModel wiring
- [ ] First bootable Android 7.1.2 ROM image
- [ ] Framebuffer → SurfaceView display pipeline
- [ ] Virtual touch/key input (uinput)

### Phase 2 — Display & Input
- [ ] Full framebuffer rendering at native resolution
- [ ] Multi-touch forwarding
- [ ] Hardware key forwarding (back, home, volume)
- [ ] Clipboard sharing host ↔ guest
- [ ] Floating overlay window mode

### Phase 3 — ROMs & Storage
- [ ] ROM CDN + manifest JSON
- [ ] In-app ROM downloader with SHA-256 verification
- [ ] Writable `/data` partition (overlayfs)
- [ ] Instance snapshot / restore
- [ ] Android 9, 11, 12 ROMs

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

**Will it work on my Dimensity 8400-Ultra phone?**  
Yes — that's one of the primary target devices. VineOS auto-detects arm64-only SoCs and enables QEMU mode automatically.

**Why GPL-3.0 and not MIT/Apache?**  
VineOS uses QEMU (GPL-2.0+) and is architecturally inspired by Anbox/Waydroid (GPL-3.0). GPL-3.0 ensures commercial forks must also open their changes, preventing another closed-source VPhoneOS-style fork.

**Can I use VineOS on x86_64 Android emulators?**  
Not currently. VineOS is ARM-only. x86 emulation support is a long-term consideration.

---

## Contributing

Contributions of all kinds are welcome. Please read **[CONTRIBUTING.md](CONTRIBUTING.md)** first.

Key areas where help is most needed right now:
- Building the Android 7.1.2 AOSP ROM image
- Cross-compiling QEMU user-mode as a static arm64 Android binary
- Framebuffer → SurfaceView display pipeline
- Testing on diverse devices, especially arm64-only SoCs

---

## License

VineOS is licensed under the **GNU General Public License v3.0**.  
See [LICENSE](LICENSE) for the full text.

```
VineOS — Free, open-source Android-on-Android virtual machine
Copyright (C) 2025 Matt (Hexadecinull) and contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

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

VineOS stands on the shoulders of:

- **[Anbox](https://github.com/anbox/anbox)** — original Android-in-a-Linux-container; VineOS's container architecture is directly inspired by its design
- **[Waydroid](https://github.com/waydroid/waydroid)** — maintained successor; invaluable reference for namespace setup and binfmt_misc integration
- **[redroid](https://github.com/remote-android/redroid-doc)** — Android in Docker; reference for mount table and device node setup
- **[QEMU](https://www.qemu.org/)** — the backbone of VineOS's 32-bit emulation
- **[VPhoneOS / VPhoneGaGa](https://vphoneos.com/)** — closed-source inspiration for what VineOS aims to replace with an open alternative
- The entire **Android homebrew and modding community**

---

<div align="center">
  <sub>Made with 🌿 by <a href="https://github.com/Hexadecinull">Hexadecinull</a> and contributors</sub>
</div>
