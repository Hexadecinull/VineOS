# Terms of Service

**Last updated: July 2026**

These terms cover your use of VineOS, a free and open-source
Android-on-Android virtualization app. By downloading, building, or using
VineOS, you agree to them.

## What VineOS is

VineOS lets you run one or more separate Android system images ("guest
instances") inside a Linux namespace container on your Android device. It
is provided as-is, for personal, educational, and development use.

## License

VineOS is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
The full license text is in [`LICENSE`](../LICENSE) at the root of the
repository. In short: you're free to use, study, modify, and redistribute
VineOS, provided any distributed derivative work is also licensed under
GPL-3.0 and its source is made available. These Terms of Service don't
override or restrict the rights the GPL already grants you; where the two
conflict for source code and redistribution, the GPL controls.

## No warranty

VineOS is provided **without warranty of any kind**, express or implied,
including but not limited to warranties of merchantability, fitness for a
particular purpose, and non-infringement. VineOS is pre-1.0, alpha-quality
software. It may be unstable, may fail to boot a given ROM, may have bugs
that affect your device's performance or battery life while running, and
its isolation model, while meaningful, is not equivalent to a hypervisor
(see [SECURITY.md](SECURITY.md#security-design-notes)).

## Limitation of liability

To the maximum extent permitted by applicable law, the VineOS maintainers
and contributors are not liable for any direct, indirect, incidental,
special, or consequential damages arising from your use of VineOS,
including but not limited to data loss, device malfunction, or any
consequence of running guest ROM images or granting root access inside a
guest instance.

## Your responsibilities

- **ROM images you run are your responsibility.** VineOS itself is
  distributed without any bundled ROM. Whether a ROM you download or build
  is legal for you to use, and whether it infringes any third party's
  rights, depends on that ROM's own source and license terms, not on
  VineOS. Official VineOS-distributed ROMs are built from unmodified AOSP
  source (see [BUILDING.md](BUILDING.md#building-aosp-rom-images)) and
  contain no proprietary Google or OEM blobs, but you're responsible for
  what you choose to install inside a guest once it's running.
- **Root access is opt-in and at your own risk.** Enabling Magisk or
  similar tooling inside a guest instance reduces that instance's
  isolation boundary; see [SECURITY.md](SECURITY.md).
- **Comply with applicable law.** You're responsible for using VineOS in a
  way that complies with the laws that apply to you, including around
  software licensing, export control, and device modification.
- **Don't use VineOS to violate someone else's terms of service.** For
  example, running apps inside a guest that are themselves prohibited from
  running in a virtualized or emulated environment by their own developer's
  terms is between you and that developer, not VineOS.

## Contributions

By submitting a pull request or other contribution to the VineOS
repository, you agree to license your contribution under GPL-3.0, and you
represent that you have the right to do so. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the contribution process.

## No affiliation

VineOS is an independent, community project. It is not affiliated with,
endorsed by, or sponsored by Google, the Android Open Source Project, or
any device manufacturer. "Android" is a trademark of Google LLC; VineOS's
use of the name is purely descriptive of the technology involved.

## Changes to these terms

These terms may be updated as the project evolves, particularly as VineOS
moves from pre-1.0 toward a stable release. Material changes will be
called out in release notes.

## Contact

Questions about these terms can be raised as a
[GitHub Issue](https://github.com/Hexadecinull/VineOS/issues).
