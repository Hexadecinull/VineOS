#!/usr/bin/env python3
"""Generate the VineOS ROM store manifest.json from a directory of .vrom files.

Reads each .vrom's own embedded manifest.json for the descriptive fields,
computes the whole-file sha256 the app actually verifies after download
(not the per-component hashes in the .vrom's internal manifest), and writes
a ready-to-serve store manifest.json. See docs/ROM_STORE_SETUP.md.
"""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

CHUNK_SIZE = 1024 * 1024


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(CHUNK_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def read_internal_manifest(vrom_path: Path) -> dict:
    with zipfile.ZipFile(vrom_path) as zf:
        with zf.open("manifest.json") as f:
            return json.load(f)


def slugify(name: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return slug or "rom"


def build_entry(vrom_path: Path, base_url: str, min_host_api_level: int) -> dict:
    internal = read_internal_manifest(vrom_path)
    required = ["displayName", "androidVersion", "apiLevel", "supportedAbis"]
    missing = [k for k in required if k not in internal]
    if missing:
        raise ValueError(f"{vrom_path.name}: internal manifest.json missing {missing}")

    file_id = slugify(vrom_path.stem)
    size_bytes = vrom_path.stat().st_size
    print(f"  hashing {vrom_path.name} ({size_bytes / 1_000_000:.0f} MB)...", file=sys.stderr)
    sha256 = sha256_of(vrom_path)

    return {
        "id": file_id,
        "displayName": internal["displayName"],
        "androidVersion": internal["androidVersion"],
        "apiLevel": internal["apiLevel"],
        "description": internal.get("description", ""),
        "downloadUrl": f"{base_url.rstrip('/')}/{vrom_path.name}",
        "sha256": sha256,
        "sizeBytes": size_bytes,
        "minHostApiLevel": internal.get("minHostApiLevel", min_host_api_level),
        "supportedAbis": internal["supportedAbis"],
        "has32BitSupport": internal.get("has32BitSupport", False),
        "releaseDate": internal.get("releaseDate", ""),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roms-dir", required=True, type=Path, help="Directory containing .vrom files")
    parser.add_argument("--base-url", required=True, help="Public base URL the roms dir is served from")
    parser.add_argument("--output", required=True, type=Path, help="Where to write manifest.json")
    parser.add_argument("--min-host-api-level", type=int, default=26)
    args = parser.parse_args()

    vrom_files = sorted(args.roms_dir.glob("*.vrom"))
    if not vrom_files:
        print(f"No .vrom files found in {args.roms_dir}", file=sys.stderr)
        return 1

    roms = []
    for vrom_path in vrom_files:
        try:
            roms.append(build_entry(vrom_path, args.base_url, args.min_host_api_level))
        except (KeyError, ValueError, zipfile.BadZipFile) as e:
            print(f"  SKIPPING {vrom_path.name}: {e}", file=sys.stderr)

    manifest = {"version": 1, "roms": roms}
    args.output.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Wrote {args.output} with {len(roms)} ROM(s)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
