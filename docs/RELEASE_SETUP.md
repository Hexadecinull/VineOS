# Setting up and running VineOS releases

Everything needed to get `.github/workflows/release.yml` working, from
zero to a signed APK attached to a GitHub Release. This is the guide
referenced from `docs/BUILDING.md`, pulled out on its own since it's
easy to miss buried in a longer doc.

## 1. Generate a signing keystore

Skip this if you already have one.

```bash
keytool -genkey -v -keystore vineos-release.jks -alias vineos -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for a store password, a key password (can be the
same as the store password), and some certificate identity fields
(name, org, etc., these can be anything, they're not security-relevant).
Keep the `.jks` file and both passwords, you need all three for both
local builds and the GitHub secrets below.

## 2. Local builds (optional, only needed if you build release APKs yourself)

Create `keystore.properties` in the project root (already in
`.gitignore`, never commit it):

```properties
storeFile=/path/to/your/vineos-release.jks
storePassword=your_store_password
keyAlias=vineos
keyPassword=your_key_password
```

`app/build.gradle.kts` reads this automatically. Without it,
`./gradlew assembleRelease` still works but produces an unsigned APK,
so a plain checkout never fails to build just because nobody's set up
signing locally.

## 3. GitHub Actions secrets

This is the part that actually matters for `release.yml`, which runs
on GitHub's servers and has no access to your local `keystore.properties`.
It needs four repository secrets instead.

In the repo on GitHub: **Settings → Secrets and variables → Actions →
New repository secret**, add each of these:

| Secret name | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The keystore file, base64-encoded (command below) |
| `RELEASE_STORE_PASSWORD` | The keystore's store password |
| `RELEASE_KEY_ALIAS` | The key alias (`vineos` if you used the command above) |
| `RELEASE_KEY_PASSWORD` | The password for that specific key |

To get the base64 value for `RELEASE_KEYSTORE_BASE64`:
```bash
base64 -w 0 vineos-release.jks > keystore.b64
cat keystore.b64
```
Copy the entire single-line output as the secret's value. Delete
`keystore.b64` afterward, it was only there to get the text onto your
clipboard, no reason to leave a plaintext copy sitting around.

**Back up the `.jks` file itself somewhere outside the repo** (password
manager file storage, an encrypted drive, wherever), in addition to it
being in GitHub's secrets. GitHub secrets are write-only once saved,
nobody, including you as a repo admin, can read them back out through
the UI. If the keystore is ever lost, every future release needs a new
signing key, and Android treats a different key as a different app:
existing installs can't upgrade in place, everyone has to uninstall
and reinstall, losing their local instance data. The file is the only
real backup, the GitHub secret alone isn't one.

## 4. Running a release

`release.yml` triggers manually, not on tag push, since releases
deserve a conscious decision after seeing CI pass rather than
happening automatically the moment a tag lands.

1. Push whatever commit you want to release (a tag isn't required to
   exist beforehand, the workflow creates the GitHub Release and its
   tag together).
2. On GitHub: **Actions → Release → Run workflow**.
3. Fill in the three inputs:
   - **Version tag**: e.g. `v0.2.0` or `v0.2.0-beta.1`, must match
     `vX.Y.Z` or `vX.Y.Z-suffix`, the workflow rejects anything else
     before it does any building.
   - **Release notes**: a short header for the GitHub Release body,
     optional, defaults to a generic placeholder line.
   - **Pre-release**: checked by default, since the project's pre-1.0.
4. Run it. The workflow: validates the tag format, builds and signs
   the release APK, builds a debug APK alongside it, runs
   `testDebugUnitTest` and `testReleaseUnitTest` as a safety gate
   before either build, verifies the release APK's signature with
   `apksigner`, then creates the GitHub Release with both APKs
   attached.

## 5. Where things end up

- The GitHub Release itself: repo → **Releases** tab, tagged with
  whatever version you entered.
- Two APKs attached to it: `VineOS-<version>.apk` (the one to
  actually install) and `VineOS-<version>-debug.apk` (debuggable,
  for troubleshooting only).
- Workflow artifacts (same two APKs, useful if you want them without
  digging through the Releases page): the workflow run's **Summary**
  tab, kept for 90 days.

## 6. If a release run fails

- **Tag format rejected**: check the `validate` job's log, it prints
  the exact regex it expects.
- **Keystore decode produces an empty file**: `RELEASE_KEYSTORE_BASE64`
  is missing, empty, or wasn't the actual base64 text (e.g. you pasted
  a filename by mistake). Re-run the `base64 -w 0` command and check
  the output isn't empty before pasting it in.
- **Unit tests fail**: same tests as `ci.yml`'s `testDebugUnitTest`,
  fix them the same way you would there, the release workflow runs
  `testReleaseUnitTest` too, see the AGP 9 unit-test-build-type note
  in `docs/BUILDING.md`'s CI/CD section if that one specifically is
  what's failing.
- **apksigner verification fails**: means signing didn't actually
  apply, almost always a wrong password in one of the four secrets,
  double check each one wasn't truncated or has a stray trailing
  newline when it was pasted in.
