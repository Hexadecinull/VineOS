# Using VineOS

This is a guide to using the VineOS app itself: downloading ROMs, creating
and running instances, and what the settings do. For building the app from
source, see [BUILDING.md](BUILDING.md); for how it works internally, see
[ARCHITECTURE.md](ARCHITECTURE.md).

## Table of Contents

- [Before you start: is your device compatible?](#before-you-start-is-your-device-compatible)
- [Downloading a ROM](#downloading-a-rom)
- [Creating an instance](#creating-an-instance)
- [Running an instance](#running-an-instance)
- [Managing instances](#managing-instances)
- [Settings](#settings)
- [Root access inside a guest](#root-access-inside-a-guest)
- [Understanding the compatibility badges](#understanding-the-compatibility-badges)
- [Troubleshooting](#troubleshooting)

## Before you start: is your device compatible?

VineOS needs `minSdk` 26 (Android 8.0) or newer, and works on `arm64-v8a`,
`armeabi-v7a`, `x86_64`, and `x86` devices. You can check your device's
supported ABIs from **Settings → About → Host ABI** inside the app, which
mirrors what `adb shell getprop ro.product.cpu.abilist` reports.

Not every ROM runs equally well on every device: a ROM built only for
32-bit ARM will run through hardware emulation (slower) on an x86_64
device, but natively (fast) on an `armeabi-v7a` or capable `arm64-v8a`
device. The [ROMs tab](#understanding-the-compatibility-badges) shows
exactly what to expect before you download anything.

## Downloading a ROM

1. Open the **ROMs** tab.
2. Each entry shows the Android version, size, and which guest CPU
   architectures it supports.
3. Tap a ROM to see its full detail page: description, supported ABIs with
   per-ABI compatibility, and whether it includes 32-bit app support.
4. Tap **Download**. VineOS verifies the download's SHA-256 hash before
   marking it ready; a corrupted or interrupted download is flagged rather
   than silently used.

ROMs are minimal AOSP builds targeting a virtual hardware platform, not
your device's specific hardware, so don't expect vendor camera apps or
carrier bloatware inside a guest. That's intentional.

## Creating an instance

From a ROM's detail page, once it's downloaded, tap **Create Instance**:

1. **Name** your instance (defaults to the ROM's display name).
2. **Pick an icon** from the emoji choices, just a visual way to tell
   instances apart on the home screen.
3. **RAM**: how much memory the guest gets. More RAM means smoother guest
   performance but less available to the rest of your device while it's
   running.
4. **Storage**: how much disk space the guest's writable partition gets.
   This is allocated up front, so pick something reasonable for what
   you'll install inside the guest.
5. Tap **Create Instance**. VineOS sets up the instance's storage and
   takes you straight into it.

## Running an instance

From the **Instances** tab, tap an instance's play button to boot it, or
tap the card itself to see its detail page first. A running instance shows
a live status indicator (booting, running, error) and can be stopped from
either the home screen or its detail page.

Once booted, the instance opens full-screen. Touch input is forwarded
directly to the guest as if it were a real touchscreen; the floating
controls at the bottom give you Android's Home and Recents/Overview
actions inside the guest, plus a way to back out to VineOS itself.

Instances keep running in the background (via a foreground service, with
a persistent notification) if you switch away from VineOS, so you can use
other apps while a guest stays booted.

## Managing instances

From an instance's detail page you can see its RAM/storage allocation,
when it was created and last used, and (with **Show diagnostics**) a raw
dump of the container's current state, mount table, and QEMU status,
useful when filing a bug report. Deleting an instance removes its storage
permanently and can't be undone; an instance must be stopped first.

## Settings

| Setting | What it does |
|---|---|
| Dynamic color | Uses Android's Material You wallpaper-based theme instead of VineOS's own brand palette |
| Keep screen on | Prevents the display from sleeping while VineOS is open |
| Default RAM / storage | Pre-fills the create-instance form; you can still override per instance |
| Show technical info | Shows kernel version, ABI, and namespace details on instance cards |
| Allow root instances | Enables the option to grant an instance root access; see below |

## Root access inside a guest

Root support (via Magisk-style tooling) is opt-in and off by default. It's
scoped to the guest, granting elevated privileges *inside that instance's
own sandbox*, not on your host device. Enabling it does narrow that
instance's isolation boundary; see
[SECURITY.md](SECURITY.md#security-design-notes) if you want the full
technical detail before turning it on.

## Understanding the compatibility badges

Each guest ABI a ROM supports is labeled with how your specific device
will run it:

| Badge | Meaning |
|---|---|
| **Native** | Runs directly on your device's CPU, best performance |
| **QEMU** | Runs through instruction-level emulation, works but slower |
| **Incompatible** | Can't run at all on your device, usually a 32-bit host trying to run a 64-bit guest |

A ROM card is grayed out and undownloadable if every ABI it supports comes
back incompatible for your device. See
[ARCHITECTURE.md](ARCHITECTURE.md#abi-compatibility) for the full
host/guest compatibility matrix if you're curious why a specific
combination lands where it does.

## Troubleshooting

**An instance won't boot / gets stuck on "Booting"**
Check its diagnostics panel for mount or namespace errors. If the ROM
shows a QEMU badge for your device, first boots can take noticeably
longer than a natively-running one.

**Downloads keep failing SHA-256 verification**
Usually an unstable connection. Delete the partial download from the
ROM's detail page and try again on a more stable connection.

**The app is slow or my battery drains fast with an instance running**
A running instance is a full Android system doing real work, similar to
running a resource-heavy app. QEMU-emulated instances cost more CPU than
natively-running ones; check the compatibility badge before committing to
a ROM if this matters to you.

**I can't find a "Create Instance" option**
It only appears on a downloaded ROM's detail page (ROMs tab → tap a ROM →
Create Instance), not directly from the Instances tab.

For anything not covered here, check existing
[GitHub Issues](https://github.com/Hexadecinull/VineOS/issues) or open a
new one with the details listed in
[CONTRIBUTING.md](CONTRIBUTING.md#issue-reporting).
