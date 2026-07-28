<div align="center">

# 🌿 VineOS

**A free, open-source, ad-free Android-on-Android virtual machine.**
Run a full isolated Android guest inside your existing device, with
**32-bit app support on arm64-only hardware** via QEMU user-mode emulation.

[![VineOS Android CI](https://github.com/Hexadecinull/VineOS/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/ci.yml)
[![VineOS CodeQL CI](https://github.com/Hexadecinull/VineOS/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/codeql.yml)
[![VineOS Dependency Review](https://github.com/Hexadecinull/VineOS/actions/workflows/dependency-review.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/dependency-review.yml)
[![VineOS Nightly Build CI](https://github.com/Hexadecinull/VineOS/actions/workflows/nightly.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/nightly.yml)
[![VineOS Release CI](https://github.com/Hexadecinull/VineOS/actions/workflows/release.yml/badge.svg?branch=main)](https://github.com/Hexadecinull/VineOS/actions/workflows/release.yml)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Hexadecinull/VineOS/total)](https://github.com/Hexadecinull/VineOS/releases)
[![GitHub Downloads (latest)](https://img.shields.io/github/downloads/Hexadecinull/VineOS/latest)](https://github.com/Hexadecinull/VineOS/releases/latest)
[![GitHub Release](https://img.shields.io/github/v/release/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/releases/latest)
[![GitHub Repo stars](https://img.shields.io/github/stars/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/stargazers)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/pulls)
[![GitHub Issues](https://img.shields.io/github/issues/Hexadecinull/VineOS)](https://github.com/Hexadecinull/VineOS/issues)

[**Download**](#installation) · [**Documentation**](docs/README.md) · [**Roadmap**](#roadmap) · [**FAQ**](#faq)

</div>

---

## Table of Contents

- [What is VineOS?](#what-is-vineos)
- [Features](#features)
- [Supported devices](#supported-devices)
- [ROM support](#rom-support)
- [Installation](#installation)
- [Documentation](#documentation)
- [Roadmap](#roadmap)
- [FAQ](#faq)
- [License](#license)
- [Credits & acknowledgements](#credits--acknowledgements)

---

## What is VineOS?

VineOS runs a complete, isolated Android operating system inside your
existing Android device, without modifying the host OS or requiring root
on most paths.

Unlike simple "app cloners" (Parallel Space, Dual Space) that sandbox
individual apps, VineOS virtualizes the full Android stack: its own
`init`, `Zygote`, `SurfaceFlinger`, `ServiceManager`, and everything else.
You get a real second phone inside your phone.

**Why VineOS over alternatives like VPhoneOS or VMOS?**

| Feature | VineOS | VPhoneOS | VMOS | Virtual Master |
|---|:---:|:---:|:---:|:---:|
| Open source | ✅ | ❌ | ❌ | ❌ |
| Ad-free | ✅ | ❌ | ❌ | ❌ |
| Free (fully) | ✅ | Partial | Partial | ❌ |
| 32-bit on arm64-only devices | ✅ | ✅ | ❌ | ❌ |
| Runs on arm, x86, and x86_64 hosts | ✅ | ❌ | ❌ | ❌ |
| GPL-3.0 licensed | ✅ | ❌ | ❌ | ❌ |
| Material You | ✅ | ❌ | ❌ | ❌ |
| Multiple ROM versions | ✅ | Paid | Partial | Paid |
| No telemetry | ✅ | ❌ | ❌ | ❌ |

---

## Features

- 🆓 **Completely free**: no paywalls, no premium tiers, no ads, ever
- 🔓 **Open source**: GPL-3.0, fully auditable
- 📦 **Multiple ROMs**: Android 7.1, 9.0, 11, 12 (more planned)
- 🏗️ **32-bit support everywhere**: runs armeabi-v7a apps on arm64-only devices via QEMU user-mode binary translation
- 🖥️ **Multi-host support**: builds and runs on arm64-v8a, armeabi-v7a, x86_64, and x86 devices, not just ARM
- 🎨 **Material You**: dynamic color from your wallpaper on Android 12+, static vine-green fallback on 8-11
- ⚡ **Lightweight**: zero background overhead when no VM is running, no persistent daemons
- 🔒 **Isolated**: PID, mount, UTS, and IPC namespace separation
- 🌱 **Multi-instance**: run multiple independent VMs simultaneously
- 🔧 **Root support**: optional Magisk/root inside instances (requires rooted host)
- 📱 **minSdk 26, targetSdk 37**: supports Android 8.0 through Android 17

---

## Supported devices

- Android 8.0 (API 26) or higher as the host OS
- `arm64-v8a`, `armeabi-v7a`, `x86_64`, or `x86` processor
- A kernel with Linux namespaces and loop device support (standard since Android 4.4)

32-bit ARM guest apps run natively where the host CPU supports it, and
through QEMU user-mode emulation everywhere else, including on the growing
range of arm64-only SoCs across many vendors, and on non-ARM hosts
entirely. See
[ARCHITECTURE.md](docs/ARCHITECTURE.md#abi-compatibility) for the full
host/guest compatibility matrix.

---

## ROM support

| ROM | Android Version | API | 32-bit | Status |
|---|---|---|---|---|
| `vine-rom-7` | Android 7.1.2 Nougat | 25 | ✅ | 🟡 In progress |
| `vine-rom-9` | Android 9.0 Pie | 28 | ✅ | 🔴 Planned |
| `vine-rom-11` | Android 11 | 30 | ✅ | 🔴 Planned |
| `vine-rom-12` | Android 12 | 31 | ✅ | 🔴 Planned |

ROMs are built from AOSP targeting the `ranchu` virtual device board and
distributed as `.vrom` archives. See
[docs/BUILDING.md](docs/BUILDING.md#building-aosp-rom-images) for the ROM
build guide.

---

## Installation

Download the latest release APK from the
[Releases page](https://github.com/Hexadecinull/VineOS/releases).

VineOS is not yet on the Play Store. First release planned at Beta
stability.

---

## Documentation

All project documentation, including the architecture guide, build
instructions, usage guide, contributing guide, and policies, lives in
[`docs/`](docs/README.md). Start there.

---

## Roadmap

### Phase 1: Foundation *(current)*
- [x] Linux namespace container runtime (`unshare`, `pivot_root`, `execl`)
- [x] QEMU binfmt_misc integration for 32-bit support (4-layer AArch32 detection)
- [x] JNI bridge with `FramebufferBridge` + `UInputBridge` wiring
- [x] Material You UI: Home, ROMs, Settings, and detail/create-instance screens
- [x] Room DB + ViewModels + DataStore preferences
- [x] ROM downloader (manifest fetch, streaming download, SHA-256 verification)
- [x] Multi-host-ABI support (arm64-v8a, armeabi-v7a, x86_64, x86)
- [ ] First bootable Android 7.1.2 ROM image

### Phase 2: Display & Input
- [x] Framebuffer render loop (mmap to ANativeWindow blit)
- [x] RGB565 to RGBA8888 conversion for Android 7.x guests
- [x] Single-touch forwarding, synced to the guest's real display resolution
- [x] Hardware key forwarding (back, home, volume, recents)
- [ ] Multi-touch forwarding (MT type B, up to 10 fingers)
- [ ] Clipboard sharing host to guest

### Phase 3: Storage & ROMs
- [ ] Writable `/data` partition (overlayfs on top of read-only ROM)
- [ ] Instance snapshot / restore
- [ ] Android 9, 11, 12 ROM images
- [ ] ROM CDN + in-app download UI completion

### Phase 4: No-root Path
- [ ] `proot` fallback for non-rooted devices
- [ ] Android Virtualization Framework (AVF) backend (Android 13+)

### Phase 5: Polish
- [ ] Play Store + F-Droid release
- [ ] Magisk inside instances
- [ ] Per-instance VPN / network isolation
- [ ] GPU passthrough research

---

## FAQ

**Does VineOS require root?**
Root is not required for the planned no-root path (Phase 4). The current
implementation requires `CAP_SYS_ADMIN` for `unshare()` and `mount()`.
Root is optional, only for Magisk inside instances.

**Does VineOS work on arm64-only phones without 32-bit support?**
Yes, this is one of the core things VineOS is built for. We're aiming to
support a wide range of SoCs across vendors, not just a specific model or
two, and VineOS auto-detects arm64-only chips and switches to QEMU mode
automatically.

**Why GPL-3.0 and not MIT/Apache?**
VineOS uses QEMU (GPL-2.0-or-later) and is architecturally inspired by
Anbox/Waydroid (GPL-3.0). GPL-3.0 ensures commercial forks must also open
their changes.

**Can I use VineOS on x86_64 Android devices or emulators?**
Yes. VineOS builds and runs on `arm64-v8a`, `armeabi-v7a`, `x86_64`, and
`x86` hosts; ARM guest ROMs run through QEMU on non-ARM hosts. See
[ARCHITECTURE.md](docs/ARCHITECTURE.md#abi-compatibility) for exactly
what runs natively versus emulated on each host.

---

## License

VineOS is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE) for the full text, and
[docs/TERMS.md](docs/TERMS.md) for the project's terms of use.

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

- **[Anbox](https://github.com/anbox/anbox)**: the original Android-in-a-Linux-container project; VineOS's container architecture is directly inspired by its design
- **[Waydroid](https://github.com/waydroid/waydroid)**: maintained successor, reference for namespace setup and binfmt_misc integration
- **[redroid](https://github.com/remote-android/redroid-doc)**: Android in Docker, reference for mount table and device node setup
- **[QEMU](https://www.qemu.org/)**: the backbone of VineOS's 32-bit emulation
- **[VPhoneOS / VPhoneGaGa](https://vphoneos.com/)**: the closed-source inspiration that VineOS aims to replace with an open alternative
- The entire Android homebrew and modding community

---

<div align="center">
  <sub>Made with 🌿 by <a href="https://github.com/Hexadecinull">Hexadecinull</a> and contributors</sub>
</div>
