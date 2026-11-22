#!/usr/bin/env bash
# Puts a new APK on the website: copies it into docs/downloads and rewrites version.json.
#
#   ./scripts/publish-apk.sh path/to/app-debug.apk 1.1 "New thing" "Another thing"
#
# Then:  git add docs && git commit -m "Smrti 1.1" && git push
set -euo pipefail
[ $# -ge 2 ] || { echo "usage: $0 <apk> <version> [note ...]"; exit 1; }
apk="$1"; version="$2"; shift 2
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/docs/downloads"
mkdir -p "$out"
cp "$apk" "$out/Smrti-latest.apk"
sha="$(sha256sum "$out/Smrti-latest.apk" | cut -d' ' -f1)"
python3 - "$out/version.json" "$version" "$sha" "$@" <<'PY'
import json, sys, datetime
path, version, sha, *notes = sys.argv[1:]
json.dump({"version": version, "date": datetime.date.today().isoformat(),
           "sha256": sha, "notes": notes or ["Bug fixes and improvements."]},
          open(path, "w"), indent=2, ensure_ascii=False)
PY
echo "Published $version -> docs/downloads/Smrti-latest.apk"
