# Contributing to VineOS

Thank you for considering a contribution. VineOS is a community-driven
project and every contribution, big or small, matters.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Before you start](#before-you-start)
- [Development setup](#development-setup)
- [Project structure](#project-structure)
- [Coding standards](#coding-standards)
- [Branching strategy](#branching-strategy)
- [Commit messages](#commit-messages)
- [Opening a pull request](#opening-a-pull-request)
- [Issue reporting](#issue-reporting)
- [Contributing ROM images](#contributing-rom-images)
- [Security vulnerabilities](#security-vulnerabilities)

## Code of Conduct

By participating in this project, you agree to be respectful to all
contributors regardless of experience level, background, or opinion.
Harassment, gatekeeping, and bad-faith criticism will not be tolerated. See
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for the full policy.

## Ways to contribute

You don't need to write code to contribute:

- **Report bugs**: open a [GitHub Issue](https://github.com/Hexadecinull/VineOS/issues) with as much detail as possible
- **Test on your device**, especially useful if you own an arm64-only device (Pixel 8, POCO X7 Pro, etc.) or a less common host ABI like x86_64
- **Improve documentation**: fix typos, add examples, clarify confusing sections in any file under [`docs/`](README.md)
- **Build ROM images**: the project currently has no bootable ROM; this is the single biggest blocker
- **Cross-compile QEMU** for additional host ABIs, see [BUILDING.md](BUILDING.md#building-qemu-arm)
- **Review pull requests**, a second pair of eyes on PRs is always helpful
- **Write code**, see the [Roadmap](../README.md#roadmap) for what's most needed

## Before you start

For anything beyond a typo fix:

1. Check existing issues and PRs to make sure nobody else is already working on it.
2. Open an issue first for significant changes (new features, architectural changes, large refactors), so effort isn't wasted if the direction isn't one the project wants to take.
3. For small, obvious fixes (typos, formatting, broken links), feel free to open a PR directly.

## Development setup

See [BUILDING.md](BUILDING.md) for the full environment setup. Short version:

- Android Studio 2025.3+ (needed for AGP 9.2 / API 37 support)
- NDK r27+, CMake 3.22+, JDK 17 (bundled with Android Studio)
- A device or emulator for testing; VineOS now builds for `arm64-v8a`,
  `armeabi-v7a`, `x86_64`, and `x86` hosts, so a wider range of hardware
  works than before

## Project structure

```
VineOS/
├── app/
│   ├── build.gradle.kts             App-level build config
│   ├── proguard-rules.pro           R8/ProGuard rules for release builds
│   ├── .gitignore                   Module-scoped ignores
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/hexadecinull/vineos/
│       │   │   ├── MainActivity.kt      Compose host + NavHost
│       │   │   ├── VineApplication.kt   Hilt app class
│       │   │   ├── ui/                  All Compose UI
│       │   │   │   ├── theme/           Material You theming
│       │   │   │   ├── navigation/      Screen definitions + nav graph
│       │   │   │   ├── screens/         HomeScreen, ROMsScreen, SettingsScreen, ...
│       │   │   │   └── components/      Reusable composables (InstanceCard, etc.)
│       │   │   ├── data/
│       │   │   │   ├── models/          VMInstance, ROMImage, AbiCompat, ...
│       │   │   │   └── repository/      InstanceRepository, ROMRepository, VineDatabase
│       │   │   ├── domain/
│       │   │   │   └── VineVMManager.kt VM lifecycle orchestrator
│       │   │   ├── service/
│       │   │   │   └── VineService.kt   Foreground service
│       │   │   └── native/
│       │   │       └── VineRuntime.kt   JNI bridge
│       │   └── cpp/                     C++17 native runtime
│       │       ├── CMakeLists.txt
│       │       ├── vine_runtime.cpp     JNI entry points
│       │       ├── container/           Linux namespace container
│       │       ├── qemu_bridge/         QEMU binary verification + binfmt_misc
│       │       ├── display/             Guest framebuffer to Surface bridge
│       │       ├── input/               Host touch/key events to guest uinput
│       │       └── utils/               Logging, filesystem, process helpers
│       └── test/, androidTest/          Unit and instrumented tests
├── docs/                             All project documentation (you are here)
├── .github/
│   ├── workflows/                    CI/CD pipelines
│   └── dependabot.yml                Automated dependency updates
├── gradle/
│   └── libs.versions.toml            Centralized dependency versions
└── README.md                         Project overview
```

## Coding standards

### Kotlin

- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Follow the [Jetpack Compose API guidelines](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/docs/compose-api-guidelines.md) for Composable functions
- 4-space indentation, no tabs
- No hardcoded strings in UI code; user-visible strings go in `res/values/strings.xml`
- Suspending functions that touch the native layer run on `Dispatchers.IO`
- Prefer `StateFlow` over `LiveData` for observable state
- `PascalCase` for Composable functions, `camelCase` for everything else
- No comments on self-explanatory code. Where a comment earns its place
  (a non-obvious workaround, a "why" that isn't visible in the code), keep
  it to one or two lines. Long comment blocks and ASCII-art section
  dividers get trimmed in review.
- `./gradlew ktlintCheck` must pass; see `.editorconfig` for the house style

```kotlin
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

- C++17 throughout, no older standards
- Follow the [Google C++ Style Guide](https://google.github.io/styleguide/cppguide.html) with one exception: `snake_case` for functions and variables, not `CamelCase`
- All code lives in the `vine` namespace (or sub-namespaces like `vine::qemu`)
- Always check syscall return values and log errors with `VINE_LOGE_ERRNO()`
- No raw `new`/`delete`; use smart pointers
- `std::string`/`std::vector`, not manual C-string allocation
- No `printf`; use the `VINE_LOG*` macros, which route to `__android_log_print`
- Same comment policy as Kotlin: short and only where genuinely useful

```cpp
bool Container::setup_binfmt_misc() {
    if (config_.qemu_arm_path.empty()) {
        VINE_LOGE("qemu_arm_path not set in config");
        return false;
    }
    // ...
}
```

### Style: dashes

Use a hyphen, comma, or period instead of an em dash in code comments, doc
strings, commit messages, and documentation. This is a house convention,
not a Kotlin or C++ standard.

## Branching strategy

| Branch | Purpose |
|---|---|
| `main` | Stable, always buildable. Direct pushes blocked. |
| `dev` | Integration branch for ongoing work. PRs merge here first. |
| `feature/<name>` | New features, branched from `dev` |
| `fix/<name>` | Bug fixes, branched from `dev` (or `main` for hotfixes) |
| `docs/<name>` | Documentation-only changes |
| `rom/<name>` | ROM image builds and tooling |

Examples: `feature/framebuffer-surfaceview`, `fix/qemu-binfmt-registration`,
`docs/building-qemu-cross-compile`, `rom/android-7-aosp-build`.

## Commit messages

We follow [Conventional Commits](https://www.conventionalcommits.org/).

```
<type>(<scope>): <short description>

[optional body]

[optional footer(s)]
```

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
| `chore` | Maintenance (updating `.gitignore`, etc.) |

Scopes (optional but encouraged): `ui`, `native`, `container`, `qemu`,
`rom`, `service`, `jni`, `theme`, `deps`.

```
feat(container): implement pivot_root namespace isolation
fix(qemu): use F flag in binfmt_misc registration for chroot compatibility
docs(building): add qemu-arm cross-compile instructions per host ABI
ci: add dependabot config for gradle and github-actions
refactor(native): move AArch32 detection to vine_utils
```

Rules: keep the subject line under 72 characters, use the imperative mood
("add" not "added"), reference issues in the footer (`Closes #42`), and
mark breaking changes with `BREAKING CHANGE:` in the footer.

## Opening a pull request

1. Fork the repo and branch from `dev` (not `main`)
2. Make your changes
3. Confirm the checks CI will run also pass locally:
   ```bash
   ./gradlew ktlintCheck
   ./gradlew lint
   ./gradlew test
   ```
4. Add or update tests if your change touches logic
5. Update documentation under `docs/` (and inline comments, sparingly) if needed
6. Open the PR against `dev`, fill in the PR template, and link related issues

CI runs lint, build, and test automatically. A maintainer will review within
a few days; requested changes are normal, not a rejection. Approved PRs are
squash-merged into `dev`.

## Issue reporting

Bug reports should include:

- VineOS version (Settings → About)
- Device model and Android version
- Host CPU ABI list (Settings → About → Host ABI, or `adb shell getprop ro.product.cpu.abilist`)
- Steps to reproduce, as specific as possible
- Expected vs actual behavior
- Logcat output: `adb logcat -s VineRuntime` during the issue, with the relevant section pasted in
- ROM version, if the issue happens inside a running instance

Feature requests should describe the use case, how you'd expect it to
work, and any alternatives considered.

## Contributing ROM images

Building and contributing AOSP-based ROM images is one of the
highest-impact ways to help. See [BUILDING.md](BUILDING.md#building-aosp-rom-images)
for the full guide. Requirements for acceptance:

- Built from AOSP source, no proprietary blobs in the system image
- Targets the `ranchu` virtual device
- Minimal size: strip unnecessary system apps, keep only what's needed to boot and run user apps
- Dual ABI: both `arm64-v8a` and `armeabi-v7a` libraries in `/system/lib` and `/system/lib64`
- SHA-256 hash provided for integrity verification
- A VineOS-specific `init.vine.rc` for VM setup
- No Google Play Services in the base image; GApps are an optional layer the user installs

ROM images are distributed via the VineOS CDN and contributed through a
manifest PR, not uploaded to the git repo directly (they're large binaries).

## Security vulnerabilities

**Do not open a public GitHub issue for security vulnerabilities.** Report
them privately through GitHub's security advisory feature; see
[SECURITY.md](SECURITY.md) for the full policy and response timeline.
