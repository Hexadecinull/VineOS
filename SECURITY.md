# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` branch | ✅ Active development |
| Tagged releases | ✅ Critical fixes backported |
| Older releases | ❌ No longer supported |

VineOS is pre-1.0. Once 1.0 is released, the policy will be updated to cover the current stable release and the previous minor version.

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report security issues privately via GitHub's security advisory feature:

**[Report a vulnerability →](https://github.com/Hexadecinull/VineOS/security/advisories/new)**

Please include:

- A description of the vulnerability and its potential impact
- Steps to reproduce (with affected Android version and device type if relevant)
- Any proof-of-concept code or screenshots
- A suggested fix if you have one

## Response Timeline

| Step | Target time |
|------|-------------|
| Acknowledgement | 48 hours |
| Initial assessment | 5 business days |
| Fix for critical issues | 7 days |
| Fix for moderate issues | 30 days |
| Public disclosure | After fix is released |

## Scope

Vulnerabilities that are in scope:

- Container namespace escape (guest process accessing host filesystem or processes)
- QEMU binfmt_misc misconfiguration enabling arbitrary code execution on host
- VineRuntime JNI vulnerabilities allowing privilege escalation
- ROM download integrity bypass (SHA-256 verification)
- VineService foreground service abuse

Out of scope:

- Vulnerabilities requiring physical device access
- Issues in upstream dependencies (QEMU, AOSP) — report those upstream
- Theoretical vulnerabilities without a practical exploit path
- Issues in the Android OS itself

## Security Design Notes

VineOS uses Linux namespaces (PID, mount, UTS, IPC) for guest isolation. This provides meaningful but not absolute isolation — it is not a hypervisor. The security model assumes:

1. The host device is not rooted by an attacker (attacker has normal Android app sandbox privileges)
2. The guest ROM image is trusted (downloaded and SHA-256 verified by VineOS)
3. SELinux is enforcing on the host

Root-required features (Magisk inside instances) are opt-in and explicitly documented as reducing the isolation boundary.
