# Privacy Policy

**Last updated: July 2026**

VineOS is a free, open-source Android app. This policy explains what data
it handles and, just as importantly, what it doesn't.

## Summary

VineOS does not have user accounts, does not run analytics or crash
reporting, does not display ads, and does not send your data to VineOS
project maintainers or any third party. Everything the app stores lives on
your device. The source code backing every claim below is public; see
[ARCHITECTURE.md](ARCHITECTURE.md) for where each piece lives.

## Data VineOS stores on your device

| Data | Where | Purpose |
|---|---|---|
| VM instance metadata (name, RAM/storage size, status) | Local Room database (`VineDatabase`) | Lets the app list and manage your instances |
| App settings (theme, default RAM, root toggle, etc.) | Jetpack DataStore preferences | Remembers your configuration between launches |
| Downloaded ROM images and per-instance rootfs data | App-private storage (`context.filesDir`) | Runs the guest Android systems you create |

None of this leaves your device unless you explicitly export or share it
yourself (for example, attaching logs to a bug report).

## Network requests VineOS makes

The only outbound network activity is:

- **Fetching the ROM manifest and downloading ROM images** you choose to
  download, from the VineOS ROM distribution CDN, over HTTPS
- Standard OS-level requests (like DNS resolution) needed to make those
  connections work

VineOS does not phone home on startup, does not check for updates outside
what your app store or GitHub Releases page already do, and does not embed
any advertising or analytics network calls.

## Permissions and why VineOS asks for them

| Permission | Why |
|---|---|
| Internet / network state | Downloading ROM images and checking connectivity before a download |
| Foreground service | Keeping a running VM instance alive while you use other apps |
| Post notifications | Showing the required foreground service notification, and download progress |
| Storage / media (scoped by Android version) | Reading ROM files you import manually, and future host-to-guest file sharing |
| Wake lock | Preventing the device from sleeping mid-boot or mid-download |
| Vibrate | Haptic feedback on touch input inside a running instance |

VineOS requests each permission only where the corresponding feature is
used, and none of them are used to collect data about you.

## Guest operating systems

Each VM instance runs a full, separate Android system image (a "ROM") that
you download and choose to run. Apps you install *inside* that guest
environment have their own privacy behavior, entirely independent of
VineOS and outside this policy's scope. Treat a VineOS guest like you
would any other Android device: review what you install into it.

## Root access (Magisk)

If you opt in to root support for an instance, you're granting elevated
privileges *inside that guest's sandbox*, not on your host device or
within VineOS itself. This is documented further in
[SECURITY.md](SECURITY.md#security-design-notes) and is off by default.

## Children's privacy

VineOS is a general-purpose systems tool and isn't directed at children.
It doesn't knowingly collect any data from anyone, of any age, since it
doesn't collect data at all in the sense this policy covers.

## Open source

VineOS is licensed under GPL-3.0. You (or anyone) can read the full source
at [github.com/Hexadecinull/VineOS](https://github.com/Hexadecinull/VineOS)
and verify every statement in this document directly against the code.

## Changes to this policy

If VineOS ever adds a feature that changes what's described here (for
example, an opt-in crash reporter), this document will be updated first,
and the change will be called out in the release notes for that version.

## Contact

Questions about this policy can be raised as a
[GitHub Issue](https://github.com/Hexadecinull/VineOS/issues). For
anything security-sensitive, use the process in
[SECURITY.md](SECURITY.md) instead of a public issue.
