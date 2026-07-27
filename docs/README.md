# VineOS Documentation

A guide to every document in this folder.

## For users

- **[USAGE.md](USAGE.md)**: how to use the app, downloading ROMs, creating
  and running instances, settings, and troubleshooting.

## For developers

- **[ARCHITECTURE.md](ARCHITECTURE.md)**: how VineOS is built, the Kotlin
  app layers, the C++ native runtime, JNI bridge, instance lifecycle,
  display and input pipelines, ABI compatibility, and CI/CD.
- **[BUILDING.md](BUILDING.md)**: building the Android app, cross-compiling
  `qemu-arm` for each supported host ABI, and building AOSP ROM images.
- **[CONTRIBUTING.md](CONTRIBUTING.md)**: how to contribute code,
  documentation, or ROM images, coding standards, branching, and commit
  message conventions.

## Policies

- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)**: expected behavior for
  everyone participating in the project.
- **[SECURITY.md](SECURITY.md)**: how to report a vulnerability, response
  timelines, and the security design model.
- **[PRIVACY.md](PRIVACY.md)**: what data the app handles, which is very
  little, and why.
- **[TERMS.md](TERMS.md)**: terms of use, license, and liability.

Everything here is kept in sync with the current state of the `main`
branch. If something looks out of date, that's a documentation bug, open
an issue or a PR.
