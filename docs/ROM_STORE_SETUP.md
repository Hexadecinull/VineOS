# Hosting your own VineOS ROM store

VineOS fetches its ROM list from a single JSON file (the "manifest"),
then downloads and verifies individual `.vrom` files from URLs listed
in that manifest. There's no special server software involved, any
plain static file host works. This guide covers a Debian box running
nginx, since that's a common, solid default, but any static HTTP(S)
server works identically.

## 1. The two manifest.json files, and why there are two

This trips people up, so it's worth being explicit up front:

- **The store manifest** (`manifest.json` at the root of your ROM
  store) lists every available ROM and is what the app fetches over
  the network. This is what this guide is about.
- **The `.vrom` internal manifest** is a *different* `manifest.json`,
  bundled *inside* each `.vrom` zip file, describing that one ROM's
  own contents. It's what powers local-file import (Home → ROMs →
  import icon) when someone picks a `.vrom` off their device with no
  network involved. See `docs/BUILDING.md`'s "ROM image format
  (.vrom)" section for that one's exact layout, you'll need it when
  you package a ROM in the first place.

Both need to exist and agree with each other (same `androidVersion`,
`apiLevel`, etc.), but they're separate files serving separate paths
through the app.

## 2. Server layout

Any layout works as long as the manifest's URLs resolve to the right
files, but a simple one:

```
/var/www/vineos-roms/
├── manifest.json
└── roms/
    ├── android-7.1.2-arm64.vrom
    ├── android-7.1.2-arm64.vrom.sha256
    └── ...
```

The `.sha256` sidecar files aren't required by the app, they're just
handy for you to `sha256sum -c` against before publishing.

## 3. The store manifest format

This is parsed directly into the app's `ROMManifest`/`ROMImage` Kotlin
classes (`app/src/main/java/com/hexadecinull/vineos/data/models/Models.kt`),
field-for-field, no renaming:

```json
{
  "version": 1,
  "roms": [
    {
      "id": "android-7.1.2-arm64",
      "displayName": "Android 7.1.2 Nougat",
      "androidVersion": "7.1.2",
      "apiLevel": 25,
      "description": "Stock AOSP 7.1.2, arm64-v8a with 32-bit compat",
      "downloadUrl": "https://vineos.hexadecinull.dpdns.org/roms/android-7.1.2-arm64.vrom",
      "sha256": "<sha256 of the WHOLE .vrom file, see section 4>",
      "sizeBytes": 734003200,
      "minHostApiLevel": 26,
      "supportedAbis": ["arm64-v8a", "armeabi-v7a"],
      "has32BitSupport": true,
      "releaseDate": "2026-01-15"
    }
  ]
}
```

Notes on specific fields:

- `id` — anything unique and URL-safe. The app also generates its own
  `local-<uuid>` IDs for locally-imported ROMs, so avoid the `local-`
  prefix here to keep the two namespaces visibly distinct.
- `downloadUrl` — must be a full, absolute URL (it's passed straight
  to OkHttp's request builder). HTTPS strongly recommended, plain HTTP
  works but the ROM (an Android system image) travels in the clear.
- `sha256` — **the hash of the entire `.vrom` file as downloaded**,
  not the per-component hashes from the `.vrom`'s own internal
  manifest. This is what `ROMRepository.verifyFile()` actually checks
  after downloading; get this one wrong and every download will fail
  verification and get deleted. Section 4 covers the exact command.
- `minHostApiLevel` — optional, defaults to 26 if omitted.
- `supportedAbis` — the ABI(s) the ROM's `system.img` was built for,
  e.g. `["arm64-v8a"]`. This drives the native/QEMU badge shown in the
  ROMs list (see `AbiCompat.romRunMode`), and armeabi-v7a can go here
  too if the image genuinely supports both.

Don't include `isLocal`, `localPath`, `isDownloaded`, or
`downloadState`, those are client-side-only fields the app fills in
itself; anything you put there in the manifest gets ignored and
overwritten on the client anyway.

## 4. Computing the sha256 the app actually checks

```bash
sha256sum android-7.1.2-arm64.vrom
```

Use that exact value (lowercase hex, the app compares
case-insensitively but lowercase is conventional) as the `sha256`
field for that ROM. Recompute and update the manifest every time you
replace a `.vrom` file, a stale hash here means every install fails
with a "SHA-256 verification failed" error and the partial download
gets deleted.

## 5. Deploying the website and ROM store

Both live on the same box, the website at `/` and the ROM store under
`/roms/` and `/manifest.json`, on the same domain. Steps 5.1-5.2 are
the same regardless of how you expose the box to the internet; 5.3
covers Cloudflare Tunnel and direct nginx exposure separately since
they genuinely differ.

### 5.1 Put the files in place

```bash
sudo mkdir -p /var/www/vineos-site /var/www/vineos-roms/roms

# Website: copy website/index.html from the repo
sudo cp website/index.html /var/www/vineos-site/index.html

# ROM store: .vrom files plus a manifest.json, generated per sections 2-4 and 7 above
sudo cp path/to/*.vrom /var/www/vineos-roms/roms/
python3 scripts/generate_manifest.py \
    --roms-dir /var/www/vineos-roms/roms \
    --base-url https://vineos.hexadecinull.dpdns.org/roms \
    --output /var/www/vineos-roms/manifest.json

sudo chown -R www-data:www-data /var/www/vineos-site /var/www/vineos-roms
```

### 5.2 nginx config

Install nginx if it's not already there (`sudo apt install nginx`),
then this config, adjust paths if you used different ones above:

```nginx
server {
    listen 80;
    server_name vineos.hexadecinull.dpdns.org;

    root /var/www/vineos-site;
    index index.html;

    location / {
        try_files $uri $uri/ =404;
    }

    location /manifest.json {
        alias /var/www/vineos-roms/manifest.json;
        add_header Cache-Control "no-cache";
        default_type application/json;
    }

    location /roms/ {
        alias /var/www/vineos-roms/roms/;
        add_header Cache-Control "public, max-age=604800, immutable";
    }
}
```

Save that as `/etc/nginx/sites-available/vineos`, then:
```bash
sudo ln -s /etc/nginx/sites-available/vineos /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

This deliberately listens on plain port 80 with no TLS block, that
part depends on which of the two paths below you use.

The website is a single static file with everything inline (styles,
script, even the logo as an embedded image), no build step, so
deploying an update is just `sudo cp website/index.html
/var/www/vineos-site/index.html` again. It fetches `/roms/manifest.json`
client-side to list available ROMs, so it'll show a "not live yet"
empty state until 5.1's manifest is actually in place, that's expected
and not a bug in either piece.

`no-cache` on the manifest means clients always revalidate before
using a cached copy, useful since you'll be editing it whenever you
add a ROM. The long `max-age` on `/roms/` is safe specifically because
`.vrom` filenames should be treated as immutable, if you update a ROM,
give the new file a new name (e.g. bump a version suffix) rather than
overwriting the old one in place, so any CDN or browser cache in front
of it can't serve stale bytes under an unchanged URL.

### 5.3 Getting the domain online

**Path A: Cloudflare Tunnel** (what you're using) — no port forwarding
on your router, no certificate on the origin box at all, Cloudflare's
edge handles public TLS and `cloudflared` makes an outbound-only
connection from your Debian box to Cloudflare, so nothing needs to
accept inbound connections from the internet directly.

```bash
# on the Debian box
sudo mkdir -p --mode=0755 /usr/share/keyrings
curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg | sudo tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null
echo 'deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared any main' | sudo tee /etc/apt/sources.list.d/cloudflared.list
sudo apt-get update && sudo apt-get install cloudflared

cloudflared tunnel login
cloudflared tunnel create vineos
cloudflared tunnel route dns vineos vineos.hexadecinull.dpdns.org
```

That last command creates the DNS record in your Cloudflare dashboard
automatically, a proxied (orange-cloud) CNAME to the tunnel, no manual
DNS editing needed. Then point the tunnel at the nginx you just set up
on port 80:

```bash
sudo mkdir -p /etc/cloudflared
sudo tee /etc/cloudflared/config.yml << 'EOF'
tunnel: vineos
credentials-file: /root/.cloudflared/<TUNNEL-ID>.json
ingress:
  - hostname: vineos.hexadecinull.dpdns.org
    service: http://localhost:80
  - service: http_status:404
EOF

sudo cloudflared service install
sudo systemctl enable --now cloudflared
```

(Swap `<TUNNEL-ID>` for the actual ID `cloudflared tunnel create` printed,
or run `cloudflared tunnel list` to look it up again.) Traffic between
`cloudflared` and Cloudflare's edge is already encrypted by the tunnel
protocol itself, and `cloudflared`-to-nginx is on localhost, so plain
HTTP for nginx here is fine, not a downgrade.

One thing worth knowing since you're already on Cloudflare: their
dashboard also has a plain **Websites → DNS** flow that only needs a
CNAME record if you'd rather not run a persistent tunnel daemon at
all, pointing the subdomain at wherever the box is reachable and
letting Cloudflare proxy it, but Tunnel is the better fit for a home
server with no static IP or open ports, which sounds like your setup.

**Path B: direct exposure with certbot** — needs port 80/443 forwarded
to the box and a stable way for `vineos.hexadecinull.dpdns.org` to
resolve to its public IP. Only relevant if you move away from
Cloudflare Tunnel later:
```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d vineos.hexadecinull.dpdns.org
```
certbot rewrites the nginx config in place to add the `443 ssl` block
and a `80 → 443` redirect automatically, you don't need to hand-write
that part.

## 6. Where the app points

`ROMRepository.kt` already hardcodes your domain:
```kotlin
private const val MANIFEST_URL = "https://vineos.hexadecinull.dpdns.org/roms/manifest.json"
```
Nothing to change here unless the domain itself changes later, in
which case that's the one line to update.

## 7. A helper script for generating the manifest

`scripts/generate_manifest.py` (added alongside this guide) scans a
directory of `.vrom` files, reads each one's embedded internal
manifest for the descriptive fields, computes the whole-file sha256
the app actually verifies, and writes out a ready-to-serve
`manifest.json`. Usage:

```bash
python3 scripts/generate_manifest.py \
    --roms-dir /var/www/vineos-roms/roms \
    --base-url https://vineos.hexadecinull.dpdns.org/roms \
    --output /var/www/vineos-roms/manifest.json
```

Re-run it any time you add, remove, or replace a `.vrom` file, it
regenerates the whole manifest from what's actually on disk rather
than hand-editing JSON, so the sha256 can never drift out of sync with
the real file.

## 8. Testing before you point real users at it

```bash
curl -s https://vineos.hexadecinull.dpdns.org/manifest.json | python3 -m json.tool
```
confirms the manifest itself is valid JSON and reachable. Beyond that,
build a debug APK pointed at your `MANIFEST_URL`, and check the ROMs
tab actually lists what you expect and that a full download completes
and passes verification, that's the real end-to-end test.

## 9. Certificate pinning

`NetworkModule.kt` has a `CertificatePinner` slot for the ROM store
host, currently disabled with placeholder values (pinning with a wrong
value breaks every download outright, so it stays off until real pins
are in).

**Since you're using Cloudflare Tunnel, read this first:** with the
domain proxied through Cloudflare (the orange-cloud DNS record
`cloudflared tunnel route dns` created), the TLS connection the app
actually makes terminates at **Cloudflare's edge**, not your origin
box. Running the command below against `vineos.hexadecinull.dpdns.org`
correctly captures whatever the app really sees, that part still
works, but it means you're pinning Cloudflare's certificate, not one
you personally control the renewal of. Cloudflare rotates their edge
certs on their own schedule, out of your control, and that cert may be
shared across many unrelated Cloudflare customers on the same shared
pool, so it's less predictable than pinning your own certbot-managed
cert would be. Given that, a reasonable call here is to **leave
pinning off** and rely on the sha256 file-integrity check (section 4)
plus Cloudflare's own edge security instead, revisit this if you ever
move off Cloudflare Tunnel to a setup where you control the origin
cert's lifecycle directly. If you do want it anyway, pin Cloudflare's
root/intermediate CA rather than the leaf, root CAs change on the
order of years, not Cloudflare's routine leaf rotation.

Generate the primary pin:

```bash
openssl s_client -connect vineos.hexadecinull.dpdns.org:443 -servername vineos.hexadecinull.dpdns.org </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

That gives you one pin (the current leaf certificate's public key
hash). Pin a **second, backup** value too, so a routine cert renewal
doesn't lock the app out before you can ship an update, either:
- run the same command against your certbot renewal's *next* cert if
  you can get at it ahead of time (only applicable to the direct
  exposure path, not Cloudflare Tunnel), or
- more simply, pin the intermediate or root certificate instead of
  (or alongside) the leaf, since those change far less often, whether
  it's Let's Encrypt's ~90-day leaf rotation or Cloudflare's own edge
  cert rotation. `openssl s_client -showcerts` against the same host
  shows the full chain; run the same pubkey/dgst pipeline against the
  intermediate or root cert in that output instead of the leaf.

Put both values (prefixed `sha256/`, matching what the commands above
already output) into `ROM_STORE_PIN_PRIMARY` and
`ROM_STORE_PIN_BACKUP` in `NetworkModule.kt`. Pinning only activates
once neither still contains the placeholder text, so a half-finished
edit fails safe rather than silently breaking downloads.

