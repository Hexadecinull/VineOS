# Architecture

This describes how VineOS is put together: the Kotlin app layers, the C++
native runtime, and how they talk to each other. For setup instructions see
[BUILDING.md](BUILDING.md); for what the app does from a user's point of
view see [USAGE.md](USAGE.md).

## Table of Contents

- [High-level overview](#high-level-overview)
- [Kotlin app layers](#kotlin-app-layers)
- [Native runtime (C++)](#native-runtime-c)
- [JNI bridge](#jni-bridge)
- [Instance lifecycle, start to stop](#instance-lifecycle-start-to-stop)
- [Display pipeline](#display-pipeline)
- [Input pipeline](#input-pipeline)
- [ABI compatibility](#abi-compatibility)
- [Data storage](#data-storage)
- [Security model](#security-model)
- [Testing strategy](#testing-strategy)
- [CI/CD](#cicd)

## High-level overview

A VineOS "instance" is a full, separate Android system image (a ROM,
packaged as a `.vrom` file) running inside a Linux namespace container on
the host device. VineOS is not a hypervisor: the guest kernel is the same
kernel as the host's. Isolation comes from PID, mount, UTS, and IPC
namespaces, plus a loop-mounted rootfs image, similar in spirit to how a
container runtime like `runc` isolates a process tree, adapted for running
a full Android `init` as the containerized process.

```
┌─────────────────────────────────────────────────────────┐
│ Host Android app (this repo)                             │
│                                                            │
│  Compose UI  →  ViewModels  →  VineVMManager (domain)     │
│                                      │                     │
│                                 VineRuntime (JNI bridge)   │
└──────────────────────────────────────┼────────────────────┘
                                        │
┌───────────────────────────────────────┼────────────────────┐
│ Native runtime (libvine_runtime.so)   │                     │
│                                        ▼                     │
│  NamespaceManager → Container  →  guest Android init (PID 1) │
│  UInputBridge      (touch/key events into the guest)         │
│  FramebufferBridge (guest display out to a host Surface)     │
│  qemu::verify_qemu_binary (32-bit ARM guest support)          │
└────────────────────────────────────────────────────────────┘
```

## Kotlin app layers

| Layer | Package | Responsibility |
|---|---|---|
| UI | `ui.screens`, `ui.components`, `ui.theme` | Jetpack Compose screens and widgets, Material You theming |
| Navigation | `ui.navigation` | `NavHost` route definitions |
| ViewModel | `ui.viewmodel` | Hilt-injected `ViewModel`s exposing `StateFlow` to the UI |
| Domain | `domain.VineVMManager` | Orchestrates VM start/stop, owns per-instance native handles and status flows |
| Data (models) | `data.models` | `VMInstance`, `ROMImage`, `DownloadProgress`, `AbiCompat` |
| Data (repository) | `data.repository` | `InstanceRepository` (Room), `ROMRepository` (network + file management), `AppPreferences` (DataStore), `VineDatabase` |
| Native bridge | `native.VineRuntime` | `external fun` declarations mapping 1:1 to JNI exports |
| Service | `service.VineService` | Foreground service keeping a running instance alive |

Screens are stateless composables that take a `ViewModel`'s exposed state
and callbacks as parameters; each `ViewModel` is the only thing that talks
to `VineVMManager` or the repositories, so UI code never touches Room,
DataStore, or JNI directly.

## Native runtime (C++)

All native code lives under `app/src/main/cpp/`, compiled as a single
shared library, `libvine_runtime.so`, per the `CMakeLists.txt` build
graph. It's built for four host ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`,
and `x86` (see [ABI compatibility](#abi-compatibility)).

| Module | Files | Responsibility |
|---|---|---|
| Container | `container/namespace_manager.{h,cpp}` | Linux namespace setup, rootfs mount, binfmt_misc registration, launching guest `init` |
| Display | `display/framebuffer_bridge.{h,cpp}` | Reads the guest framebuffer, converts pixel formats, renders to a host `ANativeWindow` |
| Input | `input/uinput_bridge.{h,cpp}` | Virtual touchscreen and keyboard via the Linux `uinput` subsystem |
| QEMU bridge | `qemu_bridge/qemu_launcher.cpp` | Validates the bundled `qemu-arm` static binary before use |
| Utils | `utils/vine_utils.{h,cpp}`, `utils/vine_log.h` | Filesystem helpers, process exec/wait helpers, AArch32 detection, logging macros |
| JNI entry | `vine_runtime.cpp` | All `Java_com_hexadecinull_vineos_native_VineRuntime_*` exports |

`NamespaceManager` is a process-wide singleton owning a handle-to-`Container`
map; handles are `int64_t` values (safe to pass as a JNI `jlong`), not raw
pointers, so the Kotlin side never holds anything that could dangle.

## JNI bridge

`VineRuntime.kt` and `vine_runtime.cpp` mirror each other one-to-one: every
`external fun` in the Kotlin object has a matching `JNIEXPORT` function.
`vine_runtime.cpp` keeps a small `InstanceRuntime` struct per active
instance (its `Container*`, `FramebufferBridge`, and `UInputBridge`) in a
`std::unordered_map` keyed by the same handle `NamespaceManager` hands out,
so a single JNI call site can reach all three subsystems for a given
instance without extra lookups.

## Instance lifecycle, start to stop

1. UI calls `VineVMManager.startInstance(instance)`.
2. `VineVMManager` starts `VineService` as a foreground service (required
   for a long-running background process on modern Android), then calls
   `VineRuntime.startInstance(...)`.
3. JNI forwards to `NamespaceManager::start_container`, which constructs a
   `Container` and calls `Container::start()`:
   - loop-mounts the `.vrom` rootfs image
   - sets up bind mounts and `/dev` nodes
   - if the host lacks AArch32 support and the guest needs it, registers
     `qemu-arm` with `binfmt_misc` so 32-bit ELFs are transparently
     executed through QEMU inside the container's mount namespace
   - execs the guest's Android `init` as PID 1 inside the new namespaces
4. `VineVMManager` polls `VineRuntime.getInstanceStatus` every two seconds
   on a background coroutine and republishes it as a `StateFlow<VMStatus>`
   that the UI collects.
5. Stopping sends `SIGTERM` to guest `init` and waits up to the configured
   timeout before falling back to `SIGKILL`; `VineService` stops itself once
   no instances remain running.

## Display pipeline

When `VMDisplayScreen` attaches its `Surface` (`VineRuntime.attachSurface`),
`FramebufferBridge::open()` mmaps the guest's virtual framebuffer device and
queries its real geometry, then a background render thread
(`FramebufferBridge::render_frame`, looped from `startRendering`) copies
each frame into the host `ANativeWindow` buffer, converting RGB565 to
RGBA8888 when the guest framebuffer format requires it. Frame pacing is
driven by `AChoreographer`, the host's real vsync signal, rather than a
fixed sleep interval, so the render thread draws exactly once per display
refresh instead of guessing at a frame budget.

## Input pipeline

Touch and key events from Compose's `pointerInput`/`KeyEvent` handlers go
through `VMDisplayViewModel` to `VineRuntime.sendTouchEvent` /
`sendKeyEvent` (JNI), which reach the instance's `UInputBridge` directly;
`Container` itself is not on this path. On the first event for an
instance, `UInputBridge` is still using a generic placeholder resolution;
`vine_runtime.cpp` syncs in the framebuffer's real queried geometry (via
`UInputBridge::set_screen_size`) before the virtual `uinput` device is
created, so touch coordinates map onto the guest's actual screen. Key
events are translated from Android `KeyEvent.KEYCODE_*` constants to Linux
`KEY_*` codes (`UInputBridge::android_to_linux_keycode`) before being
written to `/dev/uinput`; the guest's `InputReader` then sees VineOS's
virtual device exactly like a real hardware touchscreen.

## ABI compatibility

`AbiCompat.kt` is the single source of truth for what runs where. It
defines a `RunMode` (`NATIVE`, `QEMU`, `UNAVAILABLE`) and a full host ABI
by guest ABI matrix:

| Host primary ABI | arm64-v8a guest | armeabi-v7a guest | armeabi guest | x86_64 guest | x86 guest |
|---|---|---|---|---|---|
| `arm64-v8a` | Native | Native or QEMU (device-dependent) | Native or QEMU | QEMU | QEMU |
| `armeabi-v7a` | Unavailable | Native | Native | Unavailable | QEMU |
| `x86_64` | QEMU | QEMU | QEMU | Native | Native |
| `x86` | QEMU | QEMU | QEMU | Unavailable | Native |

"Unavailable" always means a 32-bit host trying to run a 64-bit guest,
which no amount of emulation makes possible. `ROMsScreen` reads this matrix
to badge each downloadable ROM as natively runnable, QEMU-emulated (with a
performance cost), or incompatible with the current device, using
`Build.SUPPORTED_ABIS` as the host side of the lookup.

This Kotlin-side matrix is only meaningful because `libvine_runtime.so`
itself is compiled for all four host ABIs (`app/build.gradle.kts`
`ndk.abiFilters`) and because a matching `libqemu_arm.so` is bundled per
host ABI under `jniLibs/<abi>/` (see
[BUILDING.md](BUILDING.md#building-qemu-arm)); without both of those, the
compatibility badges would describe capabilities the installed APK doesn't
actually have on a given device.

## Data storage

| Store | Technology | Contents |
|---|---|---|
| `VineDatabase` | Room | `VMInstance` rows: name, RAM/storage size, status, timestamps |
| App preferences | Jetpack DataStore | Theme, default RAM/storage, root toggle, and other `AppSettings` |
| Instance + ROM files | App-private storage (`context.filesDir`) | Downloaded `.vrom` images, per-instance rootfs data |

Room's `fallbackToDestructiveMigration(dropAllTables = true)` is used
instead of real migrations while the schema is still moving during the
pre-1.0 phase; this will be replaced with versioned migrations before a
1.0 release.

## Security model

Summarized here; the authoritative version is
[SECURITY.md](SECURITY.md#security-design-notes). Namespace isolation is
meaningful but not equivalent to a hypervisor boundary. The threat model
assumes an untrusted guest but a trusted host (the attacker doesn't already
have root on the host device) and a trusted, hash-verified ROM image. Root
access inside a guest (Magisk) is opt-in and explicitly narrows the
isolation boundary for that instance only.

## Testing strategy

- **Unit tests** (`app/src/test/`): JUnit4 + MockK + Truth, covering model
  logic (`VMStatus` conversion, `AbiCompat`'s compatibility matrix),
  repository behavior against mocked dependencies, and `ViewModel` state
  transitions.
- **Instrumented tests** (`app/src/androidTest/`): Room database tests
  running against a real in-memory database on-device, plus Compose UI
  tests where relevant.
- **Native code** currently has no automated tests; `CodeQL` static
  analysis runs on every push as a partial substitute (see below), and
  native logic that can reasonably move to Kotlin (like `AbiCompat`) is
  kept there specifically so it's easier to unit test.

## CI/CD

GitHub Actions workflows under `.github/workflows/`:

| Workflow | Trigger | Purpose |
|---|---|---|
| `ci.yml` | Push/PR to `main`, `dev` | ktlint, Android lint, debug APK build, unit tests |
| `codeql.yml` | Push/PR, weekly cron | Static analysis for Kotlin/Java and C/C++ |
| `dependency-review.yml` | PR | Flags newly introduced vulnerable or disallowed-license dependencies |
| `release.yml` | Tag `v*` | Builds and publishes a signed release APK |
| `nightly.yml` | Nightly cron | Debug build and smoke test |

`.github/dependabot.yml` opens weekly PRs bumping outdated Gradle
dependencies (grouped by Compose, other AndroidX, and Kotlin/KSP) and
GitHub Actions versions; those PRs go through `dependency-review.yml` like
any other PR.
