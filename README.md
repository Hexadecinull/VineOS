<div align="center">

# 🟣 YpsilonOS Android

**A free, open-source, ad-free Android-on-Android virtual machine — with root-hide, Play Integrity spoofing, and ARM-game (Pokémon GO) compatibility tuned for low-end devices.**

Run a full isolated Android guest inside your existing device, with **32-bit app support on arm64-only hardware** via QEMU user-mode emulation.

[![YpsilonOS Android CI](https://github.com/yubiiixtreme/YpsilonOS-Android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/yubiiixtreme/YpsilonOS-Android/actions/workflows/ci.yml)
[![YpsilonOS CodeQL CI](https://github.com/yubiiixtreme/YpsilonOS-Android/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/yubiiixtreme/YpsilonOS-Android/actions/workflows/codeql.yml)
[![GitHub Downloads (latest)](https://img.shields.io/github/downloads/yubiiixtreme/YpsilonOS-Android/latest)](https://github.com/yubiiixtreme/YpsilonOS-Android/releases/latest)
[![GitHub Release](https://img.shields.io/github/v/release/yubiiixtreme/YpsilonOS-Android)](https://github.com/yubiiixtreme/YpsilonOS-Android/releases/latest)
[![GitHub Repo stars](https://img.shields.io/github/stars/yubiiixtreme/YpsilonOS-Android)](https://github.com/yubiiixtreme/YpsilonOS-Android/stargazers)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/yubiiixtreme/YpsilonOS-Android)](https://github.com/yubiiixtreme/YpsilonOS-Android/pulls)
[![GitHub Issues](https://img.shields.io/github/issues/yubiiixtreme/YpsilonOS-Android)](https://github.com/yubiiixtreme/YpsilonOS-Android/issues)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

[**Download**](#installation) · [**Documentation**](docs/README.md) · [**Roadmap**](#roadmap) · [**FAQ**](#faq)

</div>

---

## Table of Contents

- [What is YpsilonOS Android?](#what-is-ypsilonos-android)
- [YpsilonOS extras (vs VineOS)](#ypsilonos-extras-vs-vineos)
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

## What is YpsilonOS Android?

YpsilonOS Android runs a complete, isolated Android operating system inside your
existing Android device, without modifying the host OS or requiring root on most paths.

Unlike simple "app cloners" (Parallel Space, Dual Space) that sandbox individual apps,
YpsilonOS virtualizes the full Android stack: its own `init`, `Zygote`,
`SurfaceFlinger`, `ServiceManager`, and everything else. You get a real second phone
inside your phone.

**Why YpsilonOS Android over alternatives like VPhoneOS or VMOS?**

| Feature | YpsilonOS | VPhoneOS | VMOS | Virtual Master |
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
| **Root-hide / Play Integrity spoof** | ✅ | ❌ | ❌ | ❌ |
| **Low-end render-scale presets** | ✅ | ❌ | ❌ | ❌ |
| **Pokémon GO / ARM-game profile** | ✅ | ❌ | ❌ | ❌ |
| **RAM guard (70% host cap)** | ✅ | ❌ | ❌ | ❌ |
| **Network isolation toggle** | ✅ | ❌ | ❌ | ❌ |
| **Auto-suspend on background** | ✅ | ❌ | ❌ | ❌ |
| **Instance backup / restore** | ✅ | ❌ | ❌ | ❌ |
| **Storage cleanup** | ✅ | ❌ | ❌ | ❌ |

---

## YpsilonOS extras (vs VineOS)

YpsilonOS Android is a fork of VineOS with compatibility and low-end optimizations
added on top:

- **🛡️ Root-hide** — renames `su`, `magisk`, and related artifacts inside the guest so
  SafetyNet / Play Integrity and game root-detection see a clean, unrooted device.
- **📄 Build.prop spoofing** — overrides `ro.build.fingerprint`, `ro.product.model`,
  `ro.debuggable`, `ro.build.type`, security patch, and more so device-integrity checks
  pass on a generic clean profile.
- **🔓 SELinux toggle** — switch the guest between enforcing and permissive; some games
  and apps require permissive SELinux to launch.
- **📉 Render-scale slider** — downscale the guest framebuffer (0.25×–1.0×) to save
  fill-rate and memory bandwidth on low-end devices (2 GB RAM, entry-level SoCs).
- **🎮 Game / preset modes** — Low-end, Balanced, High-end, and a dedicated
  **Pokémon GO (ARM games)** preset that sets RAM, CPU, render scale, root-hide,
  SELinux-permissive, and clean build props in one tap.
- **📱 Low-end friendly** — defaults tuned so the VM boots and runs on devices with as
  little as 512 MB–1 GB RAM allocated to the guest.
- **🛡️ RAM guard** — refuses to allocate more than 70% of available host RAM to a
  guest, preventing OOM crashes on low-end devices.
- **🌐 Network isolation** — per-instance toggle to block guest network access,
  stopping games/apps from phoning home.
- **😴 Auto-suspend** — suspends the VM when YpsilonOS is backgrounded, saving CPU
  and battery.
- **💾 Instance backup / restore** — export instance configs to JSON bundles for
  safekeeping or migration between devices.
- **🧹 Storage cleanup** — removes per-instance temp/cache/log files to free space.

---

## Features

- 🆓 **Completely free**: no paywalls, no premium tiers, no ads, ever
- 🔓 **Open source**: GPL-3.0, fully auditable
- 📦 **Multiple ROMs**: Android 7.1, 9.0, 11, 12 (more planned)
- 🏗️ **32-bit support everywhere**: runs armeabi-v7a apps on arm64-only devices via QEMU user-mode binary translation
- 🖥️ **Multi-host support**: builds and runs on arm64-v8a, armeabi-v7a, x86_64, and x86 devices
- 🎨 **Material You**: dynamic color from your wallpaper on Android 12+, static fallback on 8–11
- ⚡ **Lightweight**: zero background overhead when no VM is running, no persistent daemons
- 🔒 **Isolated**: PID, mount, UTS, and IPC namespace separation
- 🌱 **Multi-instance**: run multiple independent VMs simultaneously
- 🔧 **Root support**: optional Magisk/root inside instances (requires rooted host)
- ⚡ **Shizuku**: optional, no-root privilege probe via ADB/Wireless Debugging
- 🛡️ **RAM guard**: caps guest RAM at 70% of available host memory to prevent OOM
- 🌐 **Network isolation**: per-instance network blocking toggle
- 😴 **Auto-suspend**: suspends VM when app is backgrounded
- 💾 **Instance backup**: export/import instance configs as JSON bundles
- 🧹 **Storage cleanup**: removes per-instance temp/cache files
- 📱 **minSdk 26, targetSdk 37**: supports Android 8.0 through Android 17

---

## Supported devices

- Android 8.0 (API 26) or higher as the host OS
- `arm64-v8a`, `armeabi-v7a`, `x86_64`, or `x86` processor
- A kernel with Linux namespaces and loop device support (standard since Android 4.4)

32-bit ARM guest apps run natively where the host CPU supports it, and through QEMU
user-mode emulation everywhere else, including on arm64-only SoCs. See
[ARCHITECTURE.md](docs/ARCHITECTURE.md#abi-compatibility) for the full host/guest
compatibility matrix.

---

## ROM support

| ROM | Android Version | API | 32-bit | Status |
|---|---|---|---|---|
| `ypsilon-rom-7` | Android 7.1.2 Nougat | 25 | ✅ | 🟡 In progress |
| `ypsilon-rom-9` | Android 9.0 Pie | 28 | ✅ | 🔴 Planned |
| `ypsilon-rom-11` | Android 11 | 30 | ✅ | 🔴 Planned |
| `ypsilon-rom-12` | Android 12 | 31 | ✅ | 🔴 Planned |

ROMs are built from AOSP targeting the `ranchu` virtual device board and distributed
as `.vrom` archives. See [docs/BUILDING.md](docs/BUILDING.md#building-aosp-rom-images)
for the ROM build guide.

---

## Installation

Download the latest release APK from the
[Releases page](https://github.com/yubiiixtreme/YpsilonOS-Android/releases).

YpsilonOS Android is not yet on the Play Store. First release planned at Beta
stability.

---

## Documentation

All project documentation, including the architecture guide, build instructions,
usage guide, contributing guide, and policies, lives in [`docs/`](docs/README.md).
Start there.

---

## Roadmap

### Phase 1: Foundation *(current)*
- [x] Linux namespace container runtime (`unshare`, `pivot_root`, `execve`)
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
- [x] Shizuku integration to probe whether ADB-shell privilege clears `unshare`/`mount`
- [ ] `proot` fallback for non-rooted devices
- [ ] Android Virtualization Framework (AVF) backend (Android 13+)

### Phase 5: Polish
- [ ] Play Store + F-Droid release
- [ ] Magisk inside instances
- [ ] Per-instance VPN / network isolation
- [ ] GPU passthrough research

---

## FAQ

**Does YpsilonOS Android require root?**
Root is not required for the planned no-root path (Phase 4). The current
implementation requires `CAP_SYS_ADMIN` for `unshare()` and `mount()`. Root is
optional, only for Magisk inside instances.

**Does YpsilonOS Android work on arm64-only phones without 32-bit support?**
Yes — this is one of the core things it's built for. It auto-detects arm64-only SoCs
and switches to QEMU mode automatically.

**Can I run Pokémon GO and other ARM games?**
Yes. Use the **Pokémon GO (ARM games)** preset, which enables root-hide, build.prop
spoofing, SELinux-permissive, 1 GB RAM, and 0.75× render scale by default. On low-end
devices, switch to the **Low-end** preset (512 MB RAM, 0.6× render scale).

**Does YpsilonOS Android work well on low-end devices (2 GB RAM, entry-level SoC)?**
Yes. Use the **Low-end** preset (512 MB RAM, 0.6× render scale, root-hide enabled).
The **RAM guard** prevents allocating more than 70% of available host RAM, and the
**render-scale slider** reduces GPU fill-rate. Storage cleanup helps free space.

**Can I run Pokémon GO and other ARM games?**
Yes. Use the **Pokémon GO (ARM games)** preset, which enables root-hide, build.prop
spoofing, SELinux-permissive, 1 GB RAM, and 0.75× render scale by default. On low-end
devices, switch to the **Low-end** preset (512 MB RAM, 0.6× render scale).

**Can I block games from accessing the network?**
Yes. Enable **Network isolation** on the Compatibility screen to prevent the guest
from making network requests.

**Can I back up my instances?**
Yes. Use the **Backup** button on the Compatibility screen to export the instance
configuration to a JSON bundle in `Documents/YpsilonOS/backups`.

**Why GPL-3.0 and not MIT/Apache?**
YpsilonOS uses QEMU (GPL-2.0-or-later) and is architecturally inspired by Anbox/
Waydroid (GPL-3.0). GPL-3.0 ensures commercial forks must also open their changes.

---

## License

YpsilonOS Android is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE) for the full text, and [docs/TERMS.md](docs/TERMS.md) for the
project's terms of use.

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

- **[Anbox](https://github.com/anbox/anbox)**: the original Android-in-a-Linux-container project; YpsilonOS's container architecture is directly inspired by its design
- **[Waydroid](https://github.com/waydroid/waydroid)**: maintained successor, reference for namespace setup and binfmt_misc integration
- **[redroid](https://github.com/remote-android/redroid-doc)**: Android in Docker, reference for mount table and device node setup
- **[QEMU](https://www.qemu.org/)**: the backbone of YpsilonOS's 32-bit emulation
- **[VineOS / Hexadecinull](https://github.com/Hexadecinull/VineOS)**: the original project this fork is based on
- **[VPhoneOS / VPhoneGaGa](https://vphoneos.com/)**: the closed-source inspiration that YpsilonOS aims to replace with an open alternative
- The entire Android homebrew and modding community

---

<div align="center">
  <sub>Made with 🟣 by <a href="https://github.com/yubiiixtreme">yubiiixtreme</a> and contributors</sub>
</div>
