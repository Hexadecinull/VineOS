# Contributing to VineOS

First of all — thank you for considering a contribution. VineOS is a community-driven project and every contribution, big or small, matters.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Before you start](#before-you-start)
- [Development setup](#development-setup)
- [Project structure](#project-structure)
- [Coding standards](#coding-standards)
  - [Kotlin](#kotlin)
  - [C/C++ (NDK)](#cc-ndk)
- [Branching strategy](#branching-strategy)
- [Commit messages](#commit-messages)
- [Opening a pull request](#opening-a-pull-request)
- [Issue reporting](#issue-reporting)
- [Contributing ROM images](#contributing-rom-images)
- [Security vulnerabilities](#security-vulnerabilities)

---

## Code of Conduct

By participating in this project, you agree to be respectful to all contributors regardless of experience level, background, or opinion. Harassment, gatekeeping, and bad-faith criticism will not be tolerated.

---

## Ways to contribute

You don't need to write code to contribute:

- **Report bugs** — open a [GitHub Issue](https://github.com/Hexadecinull/VineOS/issues) with as much detail as possible
- **Test on your device** — especially useful if you own an arm64-only device (Pixel 8, POCO X7 Pro, etc.)
- **Improve documentation** — fix typos, add examples, clarify confusing sections
- **Build ROM images** — the project currently has no bootable ROM; this is the single biggest blocker
- **Cross-compile QEMU** — building `qemu-arm` as a static arm64 Android binary (see BUILDING.md)
- **Review pull requests** — a second pair of eyes on PRs is always helpful
- **Write code** — see the [Roadmap](README.md#roadmap) for what's most needed

---

## Before you start

For anything beyond a typo fix:

1. **Check existing issues and PRs** to make sure nobody else is already working on it.
2. **Open an issue first** for significant changes (new features, architectural changes, large refactors). This avoids wasted effort if the direction isn't something the project wants to go.
3. For small, obvious fixes (typos, formatting, broken links), feel free to open a PR directly.

---

## Development setup

See [BUILDING.md](BUILDING.md) for the full environment setup. The short version:

- **Android Studio** Ladybug (2024.2+) or newer
- **NDK** r27+
- **CMake** 3.22+
- **JDK 17** (bundled with Android Studio)
- An **arm64-v8a Android device** or emulator for testing

---

## Project structure

```
VineOS/
├── app/
│   ├── build.gradle.kts             # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/hexadecinull/vineos/
│       │   ├── MainActivity.kt      # Compose host + NavHost
│       │   ├── VineApplication.kt   # Hilt app class
│       │   ├── ui/                  # All Compose UI
│       │   │   ├── theme/           # Material You theming
│       │   │   ├── navigation/      # Screen definitions + nav graph
│       │   │   ├── screens/         # HomeScreen, ROMsScreen, SettingsScreen, ...
│       │   │   └── components/      # Reusable composables (InstanceCard, etc.)
│       │   ├── data/
│       │   │   ├── models/          # Data classes: VMInstance, ROMImage, ...
│       │   │   └── repository/      # InstanceRepository, ROMRepository
│       │   ├── domain/
│       │   │   └── VineVMManager.kt # VM lifecycle orchestrator
│       │   ├── service/
│       │   │   └── VineService.kt   # Foreground service
│       │   └── native/
│       │       └── VineRuntime.kt   # JNI bridge (18 native methods)
│       └── cpp/                     # C++17 native runtime
│           ├── CMakeLists.txt
│           ├── vine_runtime.cpp     # JNI entry points
│           ├── container/           # Linux namespace container
│           ├── qemu_bridge/         # QEMU binary verification + binfmt_misc
│           └── utils/               # Logging, filesystem, process helpers
├── .github/
│   └── workflows/                   # CI/CD pipelines
├── gradle/
│   └── libs.versions.toml           # Centralized dependency versions
├── BUILDING.md
├── CONTRIBUTING.md
└── README.md
```

---

## Coding standards

### Kotlin

- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Follow the [Jetpack Compose API guidelines](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/docs/compose-api-guidelines.md) for all Composable functions
- Use **4-space indentation**, no tabs
- **No hardcoded strings** in UI code — all user-visible strings go in `res/values/strings.xml`
- All suspending functions that interact with the native layer must run on `Dispatchers.IO`
- Prefer `StateFlow` over `LiveData` for observable state
- Name Composable functions with `PascalCase`; regular functions with `camelCase`
- Add KDoc comments on all public API classes and functions

Example of good Kotlin style:
```kotlin
/**
 * Displays a single VM instance with its current status, controls,
 * and an overflow menu for additional actions.
 */
@Composable
fun InstanceCard(
    instance: VMInstance,
    onLaunchClick: (VMInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ...
}
```

### C/C++ (NDK)

- **C++17** throughout; no older standards
- Follow the [Google C++ Style Guide](https://google.github.io/styleguide/cppguide.html) with one exception: we use `snake_case` for function and variable names (not `CamelCase`)
- All code lives in the `vine` namespace (or sub-namespaces like `vine::qemu`)
- **Always check return values** from syscalls and log errors with `VINE_LOGE_ERRNO()`
- No raw `new`/`delete` — use smart pointers (`std::unique_ptr`, `std::shared_ptr`)
- Use `std::string` and `std::vector`, not manual C-string allocation
- Never use `printf` — use the `VINE_LOG*` macros which route to `__android_log_print`
- Comment all non-obvious syscall usage with a brief explanation of why it's needed

Example of good C++ style:
```cpp
/**
 * Register qemu-arm with binfmt_misc so ARMv7 ELFs are transparently
 * executed via QEMU inside this container's mount namespace.
 */
bool Container::setup_binfmt_misc() {
    if (config_.qemu_arm_path.empty()) {
        VINE_LOGE("qemu_arm_path not set in config");
        return false;
    }
    // ...
}
```

---

## Branching strategy

| Branch | Purpose |
|---|---|
| `main` | Stable, always buildable. Direct pushes blocked. |
| `dev` | Integration branch for ongoing work. PRs merge here first. |
| `feature/<name>` | New features, branched from `dev` |
| `fix/<name>` | Bug fixes, branched from `dev` (or `main` for hotfixes) |
| `docs/<name>` | Documentation-only changes |
| `rom/<name>` | ROM image builds and tooling |

**Branch naming examples:**
- `feature/framebuffer-surfaceview`
- `fix/qemu-binfmt-registration`
- `docs/building-qemu-cross-compile`
- `rom/android-7-aosp-build`

---

## Commit messages

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

**Format:**
```
<type>(<scope>): <short description>

[optional body]

[optional footer(s)]
```

**Types:**
| Type | When to use |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no logic change |
| `refactor` | Code restructure, no behavior change |
| `perf` | Performance improvement |
| `test` | Adding or fixing tests |
| `build` | Build system / dependencies |
| `ci` | CI configuration |
| `chore` | Maintenance (updating .gitignore, etc.) |

**Scopes** (optional but encouraged):
`ui`, `native`, `container`, `qemu`, `rom`, `service`, `jni`, `theme`, `deps`

**Examples:**
```
feat(container): implement pivot_root namespace isolation
fix(qemu): use F flag in binfmt_misc registration for chroot compatibility
docs(building): add QEMU cross-compile instructions for arm64
ci: add nightly ROM build workflow
refactor(native): move AArch32 detection to vine_utils
```

**Rules:**
- Keep the subject line under 72 characters
- Use the imperative mood in the subject ("add" not "added", "fix" not "fixed")
- Reference relevant issues in the footer: `Closes #42`, `Fixes #17`
- Breaking changes must include `BREAKING CHANGE:` in the footer

---

## Opening a pull request

1. Fork the repo and create your branch from `dev` (not `main`)
2. Make your changes
3. Ensure all existing checks pass:
   ```bash
   ./gradlew ktlintCheck         # Kotlin linting
   ./gradlew lint                # Android lint
   ./gradlew test                # Unit tests
   ```
4. Add or update tests if your change touches logic
5. Update documentation (README, BUILDING.md, inline comments) if needed
6. Open the PR against `dev`, not `main`
7. Fill in the PR template completely
8. Link any related issues

**PR title** should follow the same Conventional Commits format as commit messages.

**What happens next:**
- CI runs automatically (lint, build, test)
- A maintainer will review within a few days
- You may be asked to make changes — this is normal, not a rejection
- Once approved, it will be squash-merged into `dev`

---

## Issue reporting

When opening a bug report, please include:

- **VineOS version** (from Settings → About)
- **Device model** and **Android version**
- **Host CPU ABI list** (Settings → About → Host ABI, or `adb shell getprop ro.product.cpu.abilist`)
- **Steps to reproduce** — as specific as possible
- **Expected behavior** vs **actual behavior**
- **Logcat output** — run `adb logcat -s VineRuntime` with `adb logcat` during the issue and paste the relevant section
- **ROM version** if the issue is inside a running VM

For feature requests, describe:
- The use case / problem you're solving
- How you'd expect it to work
- Any alternatives you've considered

---

## Contributing ROM images

Building and contributing AOSP-based ROM images is one of the highest-impact ways to contribute to VineOS. See [BUILDING.md](BUILDING.md) for the full ROM build guide.

ROM images must meet these requirements to be accepted:

- **Built from AOSP source** — no proprietary blobs in the system image
- **Target the `ranchu` virtual device** — this is the Android virtual hardware platform designed for VMs
- **Minimal size** — strip unnecessary system apps, keep only what's needed for the VM to boot and run user apps
- **Dual ABI** — must include both `arm64-v8a` and `armeabi-v7a` libraries in `/system/lib` and `/system/lib64`
- **SHA-256 hash** provided for integrity verification
- **Open `init.vine.rc`** included — a VineOS-specific init script for VM-specific setup
- **No Google Play Services** in the base image — GApps are an optional layer the user installs

ROM images are distributed via the VineOS CDN and must be contributed via a manifest PR (not uploaded to the git repo directly, as they are large binary files).

---

## Security vulnerabilities

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security issues privately by emailing the maintainer or using GitHub's private security advisory feature:  
**Security → Report a vulnerability** on the [GitHub repository page](https://github.com/Hexadecinull/VineOS/security/advisories/new).

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- (Optional) suggested fix

We aim to acknowledge security reports within 48 hours and resolve critical issues within 7 days.
