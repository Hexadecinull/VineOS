# Building VineOS

This document covers everything you need to build VineOS from source, including:
- The Android app (Kotlin + NDK)
- The `qemu-arm` static binary (cross-compiled for arm64-v8a)
- AOSP ROM images (advanced)

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Building the Android app](#building-the-android-app)
  - [Clone the repo](#1-clone-the-repo)
  - [Open in Android Studio](#2-open-in-android-studio)
  - [Command-line build](#3-command-line-build-optional)
  - [Build variants](#build-variants)
- [Building qemu-arm (arm64 static binary)](#building-qemu-arm-arm64-static-binary)
  - [Why this is needed](#why-this-is-needed)
  - [Build environment](#build-environment)
  - [Cross-compile qemu-arm](#cross-compile-qemu-arm)
  - [Placing the binary](#placing-the-binary)
- [Building AOSP ROM images](#building-aosp-rom-images)
  - [Overview](#overview)
  - [Host requirements](#host-requirements)
  - [Fetch AOSP source](#fetch-aosp-source)
  - [Configure for ranchu](#configure-for-ranchu)
  - [Build and package](#build-and-package)
  - [ROM image format (.vrom)](#rom-image-format-vrom)
- [CI/CD](#cicd)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Ladybug 2024.2+ | [Download](https://developer.android.com/studio) |
| Android NDK | r27+ | Install via SDK Manager in Android Studio |
| CMake | 3.22.1+ | Install via SDK Manager in Android Studio |
| JDK | 17 | Bundled with Android Studio; do not use a different JDK |
| Git | Any recent | |

### For qemu-arm cross-compilation (optional but highly recommended)

| Tool | Notes |
|---|---|
| Ubuntu 22.04+ / Debian 12+ | WSL2 works fine on Windows |
| `gcc-aarch64-linux-gnu` | AArch64 cross-compiler |
| `ninja-build` | Build tool for QEMU |
| `python3`, `python3-pip` | Required by QEMU's build system |
| `libglib2.0-dev-aarch64-cross` | QEMU dependency |
| Android NDK r27 | For libc / libstdc++ stubs |

### For ROM image builds (advanced, heavy)

| Requirement | Notes |
|---|---|
| Ubuntu 20.04 or 22.04 x86_64 | macOS and Windows are not supported for AOSP builds |
| 16+ GB RAM | 32 GB strongly recommended |
| 300+ GB free disk | AOSP source is ~200 GB; build artifacts add more |
| 8+ CPU cores | A full AOSP build takes 2–6 hours on fast hardware |
| Fast internet | AOSP source download is ~70 GB |

---

## Building the Android app

### 1. Clone the repo

```bash
git clone https://github.com/Hexadecinull/VineOS.git
cd VineOS
```

### 2. Open in Android Studio

1. Launch Android Studio
2. **File → Open** → select the `VineOS/` directory
3. Wait for Gradle sync to complete (first sync may take a few minutes)
4. Install any SDK components that Android Studio prompts for

**SDK setup via SDK Manager:**
- Android SDK Platform API 36
- NDK (Side by side) r27
- CMake 3.22.1

### 3. Build and run

**Debug build (fastest, for development):**
```bash
./gradlew assembleDebug
```

**Install on connected device:**
```bash
./gradlew installDebug
```

**Release build (minified, for distribution):**
```bash
./gradlew assembleRelease
```

> **Note:** Release builds require a signing keystore. See [Signing](#signing) below.

### Build variants

| Variant | Package suffix | Minified | Debug symbols | Use case |
|---|---|---|---|---|
| `debug` | `.debug` | No | Yes | Development |
| `release` | *(none)* | Yes | No | Distribution |

### Signing (release builds)

Create a `keystore.properties` file in the project root (this file is in `.gitignore` — never commit it):

```properties
storeFile=/path/to/your/vineos-release.jks
storePassword=your_store_password
keyAlias=vineos
keyPassword=your_key_password
```

Generate a new keystore if you don't have one:
```bash
keytool -genkey -v \
  -keystore vineos-release.jks \
  -alias vineos \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

---

## Building qemu-arm (arm64 static binary)

### Why this is needed

VineOS bundles a statically compiled `qemu-arm` binary that runs natively on arm64-v8a Android. This binary is what provides 32-bit (armeabi-v7a) app support on devices whose CPUs have dropped AArch32 execution state.

The binary must be:
- Compiled for **arm64-v8a** (AArch64) — so it runs on the host device
- **Statically linked** — no runtime library dependencies inside the container namespace
- Built from **QEMU's user-mode emulation** target (`qemu-arm`), not the full system emulator

### Build environment

Install dependencies on Ubuntu/Debian:

```bash
sudo apt update
sudo apt install -y \
    gcc-aarch64-linux-gnu \
    g++-aarch64-linux-gnu \
    ninja-build \
    python3 python3-pip \
    libglib2.0-dev \
    pkg-config \
    flex bison \
    git

pip3 install meson
```

### Cross-compile qemu-arm

```bash
# Clone QEMU (use the latest stable release tag)
git clone https://gitlab.com/qemu-project/qemu.git
cd qemu
git checkout v9.1.0   # or latest stable

# Create a cross-compilation config for Android arm64
cat > android-arm64-cross.ini << 'EOF'
[binaries]
c = 'aarch64-linux-gnu-gcc'
cpp = 'aarch64-linux-gnu-g++'
ar = 'aarch64-linux-gnu-ar'
strip = 'aarch64-linux-gnu-strip'
pkgconfig = 'aarch64-linux-gnu-pkg-config'

[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'

[properties]
# Link statically so we have no runtime dependencies
c_link_args = ['-static']
cpp_link_args = ['-static']
EOF

# Configure — build ONLY qemu-arm (user-mode emulation for 32-bit ARM)
# We disable everything except the arm linux-user target to keep binary size minimal.
mkdir build-android-arm64
cd build-android-arm64

meson setup .. \
    --cross-file ../android-arm64-cross.ini \
    --buildtype=release \
    --strip \
    -Dtarget-list=arm-linux-user \
    -Ddefault_library=static \
    -Dstatic=true \
    -Ddocs=disabled \
    -Dtests=false \
    -Dtools=disabled \
    -Dguest-agent=disabled \
    -Dslirp=disabled \
    -Dfdt=disabled \
    -Dkvm=disabled \
    -Dvnc=disabled \
    -Dsdl=disabled \
    -Dgtk=disabled \
    -Dopengl=disabled \
    -Dvirtfs=disabled

ninja -j$(nproc)
```

After a successful build, the binary will be at:
```
build-android-arm64/qemu-arm
```

Verify it:
```bash
file build-android-arm64/qemu-arm
# Expected: ELF 64-bit LSB executable, ARM aarch64, statically linked

aarch64-linux-gnu-strip build-android-arm64/qemu-arm
du -sh build-android-arm64/qemu-arm
# Stripped binary should be ~5–15 MB
```

### Placing the binary

Android NDK packages JNI libraries from `jniLibs/`. We name it with the `.so` extension so the Android build system treats it as a native library and extracts it to the device's native lib directory:

```bash
mkdir -p app/src/main/jniLibs/arm64-v8a/
cp build-android-arm64/qemu-arm app/src/main/jniLibs/arm64-v8a/libqemu_arm.so
```

At runtime, VineOS finds it at:
```kotlin
context.applicationInfo.nativeLibraryDir + "/libqemu_arm.so"
```

> **Note:** The `jniLibs/` directory is in `.gitignore` because the binary is ~10 MB. It will be distributed as a GitHub Release asset and fetched by CI during builds.

---

## Building AOSP ROM images

> This is an advanced, resource-intensive process. Most contributors won't need to do this — only the ROM images need to be built once per Android version and then distributed via the VineOS CDN.

### Overview

VineOS ROM images are minimal AOSP system images targeting the **`ranchu`** virtual device (Android's reference virtual hardware platform, also used by the Android Emulator). We strip unnecessary apps and services to minimize image size.

### Host requirements

- Ubuntu 20.04 or 22.04 x86_64 (other distros may work but are untested)
- 16+ GB RAM (32 GB recommended)
- 300+ GB free disk space
- Python 3.9+

Install AOSP build dependencies:
```bash
sudo apt update
sudo apt install -y \
    git gnupg flex bison build-essential zip curl \
    libc6-dev libncurses5 x11proto-core-dev libx11-dev \
    libgl1-mesa-dev libxml2-utils xsltproc unzip fontconfig \
    python3-pip python3-setuptools \
    openjdk-11-jdk
```

Install `repo`:
```bash
mkdir -p ~/bin
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo
echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

### Fetch AOSP source

For the Android 7.1.2 ROM (`vine-rom-7`):

```bash
mkdir ~/aosp-7 && cd ~/aosp-7
repo init -u https://android.googlesource.com/platform/manifest \
    -b android-7.1.2_r39 \
    --depth=1   # Shallow clone saves ~50 GB

repo sync -c -j$(nproc) --no-tags --no-clone-bundle
```

> The `-b android-7.1.2_r39` tag is the last stable Android 7.1.2 release. Use a similar tag for other versions.

### Configure for ranchu

The `ranchu` target produces a virtual hardware image suitable for VMs:

```bash
cd ~/aosp-7
source build/envsetup.sh
lunch aosp_arm64-userdebug
# For dual-ABI (arm64 + armeabi-v7a), use: aosp_arm64-userdebug
```

Apply VineOS-specific patches (minimal patches to reduce image size and add VineOS init support):
```bash
# These patches will be maintained in the VineOS repo under /rom-patches/
# For now, see the rom-patches/ directory in this repository.
git apply ../VineOS/rom-patches/android-7/*.patch
```

### Build and package

```bash
make -j$(nproc) 2>&1 | tee build.log

# This produces system.img, ramdisk.img, kernel, etc.
# in out/target/product/generic_arm64/
```

### ROM image format (.vrom)

A `.vrom` file is a simple archive containing:

```
vine-rom-7.vrom  (zip archive)
├── manifest.json       ← ROM metadata (version, sha256s, API level, etc.)
├── system.img          ← ext4 image: the Android /system partition
├── vendor.img          ← ext4 image: /vendor partition (minimal)
├── ramdisk.img         ← Android ramdisk (contains /init)
└── kernel              ← Prebuilt Linux kernel for ranchu
```

**manifest.json format:**
```json
{
  "id": "vine-rom-7",
  "displayName": "Android 7.1.2 Nougat",
  "androidVersion": "7.1.2",
  "apiLevel": 25,
  "sha256": {
    "system.img": "abc123...",
    "vendor.img": "def456...",
    "ramdisk.img": "ghi789..."
  },
  "sizeBytes": 524288000,
  "supportedAbis": ["arm64-v8a", "armeabi-v7a"],
  "has32BitSupport": true,
  "releaseDate": "2025-01-01",
  "vineosMinVersion": "0.1.0"
}
```

Package it:
```bash
cd out/target/product/generic_arm64/
zip -0 vine-rom-7.vrom system.img vendor.img ramdisk.img kernel
# Add the manifest:
python3 -c "
import json, hashlib, os
files = ['system.img', 'vendor.img', 'ramdisk.img', 'kernel']
sha256s = {}
for f in files:
    with open(f, 'rb') as fp:
        sha256s[f] = hashlib.sha256(fp.read()).hexdigest()
print(json.dumps({'sha256': sha256s}, indent=2))
" > manifest.json
zip vine-rom-7.vrom manifest.json
```

---

## CI/CD

VineOS uses **GitHub Actions** for CI. The workflows are in `.github/workflows/`.

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | Push / PR to `main`, `dev` | Lint (ktlint + Android lint), build debug APK, run unit tests |
| `release.yml` | Tag `v*` | Build release APK, create GitHub Release, upload APK |
| `nightly.yml` | Cron (nightly) | Build debug APK, smoke-test |

---

## Troubleshooting

**`unshare() failed: Operation not permitted`**  
The host kernel or SELinux policy is blocking namespace creation. This requires root or a privileged context. Check `adb shell dmesg | grep denied` for SELinux denials.

**`pivot_root failed` — falling back to `chroot`**  
Some kernel configurations disable `pivot_root` for non-root users. The fallback `chroot` still works but provides weaker isolation.

**QEMU binary not found at `nativeLibraryDir/libqemu_arm.so`**  
The `jniLibs/arm64-v8a/libqemu_arm.so` file is missing. Follow the [Building qemu-arm](#building-qemu-arm-arm64-static-binary) instructions above and place the binary before building the APK.

**`mount(rootfs) failed`**  
The loop device couldn't mount the ROM image. Possible causes:
- ROM image is corrupted (re-download and verify SHA-256)
- The filesystem type (ext4/squashfs) isn't supported by the host kernel (very unlikely on modern Android)
- Insufficient permissions (check `dmesg` for details)

**Gradle sync fails with `NDK not found`**  
Open **SDK Manager → SDK Tools → NDK (Side by side)** and install NDK r27. Make sure `local.properties` has the correct `sdk.dir` pointing to your Android SDK location.

**QEMU cross-compile fails with missing `libglib-2.0`**  
```bash
sudo apt install libglib2.0-dev:arm64
# or use the cross variant:
sudo apt install libglib2.0-dev-aarch64-cross
```
Then re-run meson with `--pkg-config-path /usr/lib/aarch64-linux-gnu/pkgconfig`.
