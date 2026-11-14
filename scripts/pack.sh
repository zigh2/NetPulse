#!/usr/bin/env bash
# Packs the project into dist/NetPulse-v<VERSION>.zip - ready to drop into incoming/.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="$(tr -d ' \r\n' < VERSION)"
OUT="dist/NetPulse-v${VERSION}.zip"
mkdir -p dist
rm -f "$OUT"
zip -qr "$OUT" . \
  -x ".git/*" "dist/*" "*/build/*" "build/*" ".gradle/*" "*/.gradle/*" "local.properties" ".idea/*" "incoming/*.zip"
echo "Created $OUT"
