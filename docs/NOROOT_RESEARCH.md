# No-root path research: ADB shell privilege elevation

Notes toward Phase 4 (`docs/../README.md` roadmap). Not implemented yet;
this is a technical brief to decide *whether* the Shizuku/Virtual Master
technique gets VineOS far enough to be worth building, before committing
engineering time to it.

## What Virtual Master and Shizuku actually do

Both are closed-source (Virtual Master) or open-source (Shizuku), but
Shizuku's mechanism is fully documented and Virtual Master is understood
to work the same way: they start a background process authenticated as
the **ADB shell user (UID 2000)** instead of the app's own UID, using
Android's Wireless Debugging feature (Developer options → Wireless
debugging, Android 11+) to do this entirely on-device with no PC:

1. The app enables Wireless Debugging via `WRITE_SECURE_SETTINGS` (a
   permission normal apps can be granted without root, either by the
   user via `adb shell pm grant` once, or by some apps via an
   accessibility-service-driven UI walk).
2. It runs an embedded ADB client that pairs with the device's own
   `adbd` over `127.0.0.1`, using the same TLS pairing-code protocol a
   desktop `adb pair` would use. The phone is simultaneously the ADB
   host and the ADB target.
3. Once paired and connected, it starts a helper process (`app_process`
   running a Java entrypoint, in Shizuku's case) via the ADB shell
   connection. That process inherits the **`shell`** SELinux domain and
   UID 2000, not the app's own `untrusted_app` domain and app UID.
4. Other apps talk to that helper over Binder IPC as a broker: "ask
   shell to do X on my behalf."

This is a real, working technique, confirmed by both Shizuku's own docs
and a third-party technical writeup of malware replicating the same
flow (HackTricks). It needs Android 11+ for the on-device pairing step;
older versions need a one-time PC-based `adb` pair.

## What shell (UID 2000) actually unlocks, confirmed from AOSP sepolicy

I pulled the current AOSP `system/sepolicy` source directly rather than
going on secondhand descriptions, since this is the part that decides
whether the technique is useful for VineOS specifically.

**Confirmed: `untrusted_app` (what VineOS's own process runs as today)
is flatly blocked from cgroups, full stop:**

```
# private/app_neverallows.te
neverallow all_untrusted_apps cgroup:file *;
neverallow all_untrusted_apps cgroup_v2:file *;
```

This directly affects the RAM/CPU cgroup limiting added in the last
round of changes (`Container::setup_resource_limits()`): on a stock
device with SELinux enforcing, that code cannot work from VineOS's own
process, full stop — no capability workaround fixes an SELinux
`neverallow`. It's consistent with the project's own documented
"currently requires CAP_SYS_ADMIN" status; I'm calling it out
specifically because it's the concrete mechanism behind that
requirement for this one subsystem.

**Confirmed: `shell` gets read access to cgroups, not write:**

```
# private/shell.te
r_dir_file(shell, cgroup)
r_dir_file(shell, cgroup_v2)
```

`r_dir_file` is search/open/read/getattr only. I did not find a write
grant for shell on cgroup files in the base AOSP policy. So moving the
cgroup-limiting code to run as shell (via a Shizuku-style helper)
would not obviously unlock it either — shell can read `/sys/fs/cgroup`
(that's how `top`/`dumpsys` work), not write to it. Real `adb`-driven
priority/resource controls I'm aware of (e.g. what `am`/`cmd` commands
do) go through a Binder call to `system_server`, which does the actual
cgroup write itself, rather than the shell process writing cgroup files
directly.

**Not confirmed either way: whether shell can `mount()` / `pivot_root()`
/ `unshare()` the namespace types VineOS's container engine needs.**
I did not find an explicit capability grant (`allow shell self:capability
sys_admin`) in `shell.te`, and didn't find a `neverallow` blocking it for
app domains either, in the files I was able to pull. This needs an
empirical answer, not more source-reading — see "Next step" below.

## The one path that doesn't depend on any of the above

Regardless of what shell can or can't do, `CLONE_NEWUSER` (creating an
*unprivileged* user namespace) does not require `CAP_SYS_ADMIN` from any
UID, app or shell, as long as the kernel allows it
(`CONFIG_USER_NS_UNPRIVILEGED` / `kernel.unprivileged_userns_clone`,
device-dependent) and SELinux doesn't specifically block `userns_create`
for the calling domain. Inside that new namespace the calling process
becomes "fake root" with real `CAP_SYS_ADMIN`, but only over resources
created within that same namespace (a new mount namespace, new pid
namespace, etc. — this is the standard rootless-container technique
used by `bubblewrap`, `podman`, and `unshare --map-root-user`).

This is the one mechanism that could plausibly work from an
**untrusted_app process with no ADB/shell involvement at all**, if the
device's kernel and SELinux policy allow it. It's also the one AOSP has
been actively locking down over the years specifically because it's a
common kernel-exploit surface, so it is genuinely device/vendor/Android-
version dependent, more so than most of what's in `namespace_manager.cpp`
today.

## Net assessment

- The shell/Wireless-Debugging route is real and gets VineOS a
  meaningfully more privileged domain than a plain app, for free, with
  no root prompt. But based on what I could verify from the actual
  policy source, it does **not** obviously clear the specific bar
  `namespace_manager.cpp` needs (`CAP_SYS_ADMIN` for mount/pivot_root,
  cgroup writes) — shell looks read-mostly for exactly the things this
  project needs to write.
- The `CLONE_NEWUSER` unprivileged-namespace route is the one with a
  real shot at working without *any* elevation (root or shell), but its
  availability is the least predictable of the three, since it's
  exactly the kind of thing OEMs and newer AOSP releases restrict.
- Realistically, the strongest no-root path is probably **both
  combined**: use Wireless Debugging to get a shell-domain helper
  running (for the things shell demonstrably can do — reading, some
  property/service access, and potentially unlocking `unshare` even if
  cgroup writes stay blocked), and have that helper create the
  unprivileged user namespace rather than relying on it directly having
  `CAP_SYS_ADMIN`. Whether cgroup limits end up available at all on a
  no-root install is a separate, secondary question from whether the
  container can boot.

## Next step (needs a real device, not more reading)

The fastest way to resolve the open question is empirical, not further
policy archaeology. This is now built directly into the app rather than
left as a manual exercise: Settings → Shizuku, once Shizuku itself is
installed and connected (via Wireless Debugging or root), has a "Test
namespace access" button. It binds a Shizuku user service that runs in
whatever process Shizuku is hosting (shell UID 2000 for the ADB/Wireless
Debugging path, uid 0 for the root path), and from there attempts the
exact two operations `namespace_manager.cpp`'s `launch_init()` depends
on: `unshare(CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWUTS|CLONE_NEWIPC)` followed
by a private recursive remount of `/`, both from a disposable forked
child so the probe can't leave the long-lived service process in a
half-unshared state. It reports back which of the two succeeded and the
`errno` for whichever didn't, straight to the Settings screen. See
`app/src/main/cpp/diagnostics/namespace_probe.cpp` and
`app/src/main/java/com/hexadecinull/vineos/shizuku/` for the
implementation.

This turns the open question from source-reading into a one-tap check
on whatever device it's run on. It settles the `unshare`/`mount`
question directly; it does not settle the separate cgroup-write question
from earlier in this document (the probe only checks the two calls the
container engine's `launch_init()` actually needs to start; cgroup
limits are a secondary, independent concern that can degrade gracefully
without blocking boot).

This document should get updated with real findings once that's been
tried on real hardware, and it should also be re-checked against the
Virtual Master and similar closed-source apps' actual behavior if it
ever becomes possible to observe them (e.g. via `strace`/`dmesg` on a
rooted test device running the closed-source app itself, which would
show directly what syscalls it relies on rather than guessing from
policy alone).
