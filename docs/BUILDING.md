# Building VineOS

This covers building VineOS from source: the Android app (Kotlin + NDK), the
`qemu-arm` static binaries (one per host ABI), and AOSP ROM images.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Building the Android app](#building-the-android-app)
- [Building qemu-arm](#building-qemu-arm)
- [Building AOSP ROM images](#building-aosp-rom-images)
- [CI/CD](#cicd)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required

| Tool | Version | Notes |
|---|---|---|
| Android Studio | 2025.3+ (Narwhal or newer) | Needed for AGP 9.2 / API 37 support. [Download](https://developer.android.com/studio) |
| Android NDK | r27+ | Install via SDK Manager |
| CMake | 3.22.1+ | Install via SDK Manager |
| JDK | 17 | Bundled with Android Studio, do not substitute a different JDK |
| Git | Any recent | |

VineOS targets `compileSdk`/`targetSdk` 37 (Android 17) with `minSdk` 26
(Android 8.0). Gradle 9.5+ and AGP 9.2+ are required; AGP 9 compiles Kotlin
directly (no separate `org.jetbrains.kotlin.android` plugin), so an older
AGP will not sync this project.

### For qemu-arm cross-compilation (recommended)

| Tool | Notes |
|---|---|
| Ubuntu 22.04+ / Debian 12+ | WSL2 works fine on Windows |
| A cross-compiler per target host ABI | see the [qemu-arm](#building-qemu-arm) section |
| `ninja-build`, `python3`, `meson` | QEMU's build system |
| `libglib2.0-dev` (cross variant per target) | QEMU dependency |

### For ROM image builds (advanced, heavy)

| Requirement | Notes |
|---|---|
| Ubuntu 20.04 or 22.04 x86_64 | macOS and Windows aren't supported for AOSP builds |
| 16+ GB RAM | 32 GB strongly recommended |
| 300+ GB free disk | AOSP source is ~200 GB, build artifacts add more |
| 8+ CPU cores | A full AOSP build takes 2 to 6 hours on fast hardware |
| Fast internet | AOSP source download is ~70 GB |

## Building the Android app

### 1. Clone and open

```bash
git clone https://github.com/Hexadecinull/VineOS.git
cd VineOS
```

Open the `VineOS/` directory in Android Studio, let Gradle sync finish, and
install any SDK components it prompts for (Android SDK Platform 37, NDK r27,
CMake 3.22.1).

### 2. Build and run

```bash
./gradlew assembleDebug      # Debug build, fastest for development
./gradlew installDebug       # Install on a connected device
./gradlew assembleRelease    # Minified release build, needs a signing keystore
```

### Build variants

| Variant | Package suffix | Minified | Debug symbols | Use case |
|---|---|---|---|---|
| `debug` | `.debug` | No | Yes | Development |
| `release` | *(none)* | Yes (ProGuard/R8, see `app/proguard-rules.pro`) | No | Distribution |

### Signing (release builds)

Create a `keystore.properties` file in the project root (already covered by
`.gitignore`, never commit it):

```properties
storeFile=/path/to/your/vineos-release.jks
storePassword=your_store_password
keyAlias=vineos
keyPassword=your_key_password
```

Generate a new keystore if you don't have one:
```bash
keytool -genkey -v -keystore vineos-release.jks -alias vineos -keyalg RSA -keysize 2048 -validity 10000
```

## Building qemu-arm

### Why it's needed, and why there's more than one binary now

VineOS bundles a statically linked `qemu-arm` binary that lets the guest run
32-bit ARM (armeabi-v7a) code on hosts whose CPU doesn't support AArch32
execution. Since VineOS now supports running on `arm64-v8a`, `armeabi-v7a`,
`x86_64`, and `x86` host devices (see `AbiCompat.kt` and
[ARCHITECTURE.md](ARCHITECTURE.md#abi-compatibility)), `qemu-arm` needs to be
cross-compiled once **per host ABI** so each one gets a binary it can execute
natively. There is no cross-ABI sharing: an x86_64 host cannot run an
arm64-v8a `qemu-arm` binary any more than it can run the guest code directly.

| Host ABI | jniLibs subdirectory | Typical cross-compiler prefix |
|---|---|---|
| `arm64-v8a` | `app/src/main/jniLibs/arm64-v8a/` | `aarch64-linux-gnu-` |
| `armeabi-v7a` | `app/src/main/jniLibs/armeabi-v7a/` | `arm-linux-gnueabihf-` |
| `x86_64` | `app/src/main/jniLibs/x86_64/` | `x86_64-linux-gnu-` (native on an x86_64 build host) |
| `x86` | `app/src/main/jniLibs/x86/` | `i686-linux-gnu-` |

Each binary must be statically linked (no runtime library dependencies
inside the container's mount namespace) and built from QEMU's user-mode
emulation target, not the full system emulator.

### Build environment (example: arm64-v8a host target)

```bash
sudo apt update
sudo apt install -y gcc-aarch64-linux-gnu g++-aarch64-linux-gnu \
    ninja-build python3 python3-pip libglib2.0-dev pkg-config flex bison git
pip3 install meson
```

Swap the `gcc-aarch64-linux-gnu`/`g++-aarch64-linux-gnu` packages for the
matching cross-compiler when targeting a different host ABI (for example
`gcc-arm-linux-gnueabihf` for `armeabi-v7a`, or the system `gcc`/`g++` when
building natively for `x86_64`).

### Cross-compile qemu-arm

```bash
git clone https://gitlab.com/qemu-project/qemu.git
cd qemu
git checkout v9.1.0   # or latest stable

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
c_link_args = ['-static']
cpp_link_args = ['-static']
EOF

mkdir build-android-arm64 && cd build-android-arm64
meson setup .. \
    --cross-file ../android-arm64-cross.ini \
    --buildtype=release --strip \
    -Dtarget-list=arm-linux-user \
    -Ddefault_library=static -Dstatic=true \
    -Ddocs=disabled -Dtests=false -Dtools=disabled \
    -Dguest-agent=disabled -Dslirp=disabled -Dfdt=disabled \
    -Dkvm=disabled -Dvnc=disabled -Dsdl=disabled -Dgtk=disabled \
    -Dopengl=disabled -Dvirtfs=disabled

ninja -j$(nproc)
```

Repeat with a matching cross-file (swap the `[binaries]` prefix and
`cpu_family`/`cpu`) for each additional host ABI you want to support. Verify
and strip each binary:

```bash
file build-android-arm64/qemu-arm
# Expected: ELF 64-bit LSB executable, ARM aarch64, statically linked
aarch64-linux-gnu-strip build-android-arm64/qemu-arm
du -sh build-android-arm64/qemu-arm   # typically ~5-15 MB stripped
```

### Placing the binaries

Android NDK packages JNI libraries from `jniLibs/`. Naming the binary with a
`.so` extension makes the Android build system treat it as a native library
and extract it to the device's native lib directory at install time:

```bash
mkdir -p app/src/main/jniLibs/arm64-v8a/
cp build-android-arm64/qemu-arm app/src/main/jniLibs/arm64-v8a/libqemu_arm.so
```

At runtime, VineOS finds it via `context.applicationInfo.nativeLibraryDir +
"/libqemu_arm.so"`, which resolves to the copy matching the device's own
primary ABI automatically, no Kotlin-side ABI branching needed.

> `jniLibs/` is in `.gitignore` because these binaries are large. They're
> distributed as GitHub Release assets and fetched by CI during builds.

## Building AOSP ROM images

This is advanced and resource-intensive. Most contributors won't need to do
this; ROM images only need building once per Android version and are then
distributed via the VineOS CDN.

### Overview

VineOS ROM images are minimal AOSP system images targeting the `ranchu`
virtual device (the same reference virtual hardware platform the Android
Emulator uses), stripped of unnecessary apps and services.

### Host requirements

- Ubuntu 20.04 or 22.04 x86_64 (other distros may work but are untested)
- 16+ GB RAM (32 GB recommended), 300+ GB free disk, Python 3.9+

```bash
sudo apt update
sudo apt install -y git gnupg flex bison build-essential zip curl \
    libc6-dev libncurses5 x11proto-core-dev libx11-dev libgl1-mesa-dev \
    libxml2-utils xsltproc unzip fontconfig python3-pip python3-setuptools \
    openjdk-11-jdk

mkdir -p ~/bin
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo
echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc && source ~/.bashrc
```

### Fetch AOSP source

For the Android 7.1.2 ROM (`vine-rom-7`):

```bash
mkdir ~/aosp-7 && cd ~/aosp-7
repo init -u https://android.googlesource.com/platform/manifest \
    -b android-7.1.2_r39 --depth=1
repo sync -c -j$(nproc) --no-tags --no-clone-bundle
```

Use a similar release tag for other Android versions.

### Configure, build, and package

```bash
cd ~/aosp-7
source build/envsetup.sh
lunch aosp_arm64-userdebug   # dual-ABI: arm64-v8a + armeabi-v7a in the guest

git apply ../VineOS/rom-patches/android-7/*.patch

make -j$(nproc) 2>&1 | tee build.log
# Produces system.img, ramdisk.img, kernel, etc. in out/target/product/generic_arm64/
```

### ROM image format (.vrom)

A `.vrom` file is a zip archive:

```
vine-rom-7.vrom
├── manifest.json    ROM metadata: version, sha256s, API level, etc.
├── system.img       ext4 image, the Android /system partition
├── vendor.img       ext4 image, /vendor partition (minimal)
├── ramdisk.img      Android ramdisk, contains /init
└── kernel           Prebuilt Linux kernel for ranchu
```

`manifest.json`:
```json
{
  "id": "vine-rom-7",
  "displayName": "Android 7.1.2 Nougat",
  "androidVersion": "7.1.2",
  "apiLevel": 25,
  "sha256": { "system.img": "...", "vendor.img": "...", "ramdisk.img": "..." },
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
python3 -c "
import json, hashlib
files = ['system.img', 'vendor.img', 'ramdisk.img', 'kernel']
sha256s = {f: hashlib.sha256(open(f, 'rb').read()).hexdigest() for f in files}
print(json.dumps({'sha256': sha256s}, indent=2))
" > manifest.json
zip vine-rom-7.vrom manifest.json
```

## CI/CD

VineOS uses GitHub Actions, defined in `.github/workflows/`.

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | Push/PR to `main`, `dev` | ktlint, Android lint, debug APK build, unit tests |
| `codeql.yml` | Push/PR, weekly cron | Static analysis for Kotlin/Java and C/C++ |
| `dependency-review.yml` | PR | Flags newly introduced vulnerable or disallowed-license dependencies |
| `release.yml` | Tag `v*` | Release APK build, GitHub Release, APK upload |
| `nightly.yml` | Nightly cron | Debug APK build, smoke test |

Dependabot (`.github/dependabot.yml`) opens weekly PRs for outdated Gradle
dependencies and GitHub Actions versions; `dependency-review.yml` then runs
against those PRs like any other, so the two don't conflict.

## Troubleshooting

**`unshare() failed: Operation not permitted`**
The host kernel or SELinux policy is blocking namespace creation. Check
`adb shell dmesg | grep denied` for SELinux denials.

**`pivot_root failed`, falling back to `chroot`**
Some kernel configurations disable `pivot_root` for non-root users. The
`chroot` fallback still works but provides weaker isolation.

**QEMU binary not found at `nativeLibraryDir/libqemu_arm.so`**
The matching `jniLibs/<abi>/libqemu_arm.so` is missing for the device's host
ABI. See [Building qemu-arm](#building-qemu-arm).

**`mount(rootfs) failed`**
The loop device couldn't mount the ROM image: it may be corrupted
(re-download and verify SHA-256), or there's a permissions issue (check
`dmesg`).

**Gradle sync fails with `NDK not found`**
Install NDK r27 via SDK Manager, and confirm `local.properties` has the
correct `sdk.dir`.

**Gradle sync fails on the `kotlin-android` plugin**
AGP 9's built-in Kotlin support means `org.jetbrains.kotlin.android` is no
longer applied. If a fork or branch still references it, remove that plugin
alias; see the [AGP 9 migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin).

**QEMU cross-compile fails with missing `libglib-2.0`**
```bash
sudo apt install libglib2.0-dev-aarch64-cross   # or the matching cross variant
```
Then re-run meson with `--pkg-config-path` pointing at the cross pkgconfig dir.
