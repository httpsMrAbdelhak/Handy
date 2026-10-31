#!/usr/bin/env bash
# Fetches whisper.cpp at a pinned tag into android/third_party/whisper.cpp.
# Run from anywhere; used by CI and for local builds.
set -euo pipefail
TAG="v1.9.5"
COMMIT="d1be6fde11ac6e0407606b4e42fe72d34add8037"
DEST="$(cd "$(dirname "$0")/.." && pwd)/third_party/whisper.cpp"
if [ -f "$DEST/CMakeLists.txt" ] && [ "$(git -C "$DEST" rev-parse HEAD 2>/dev/null)" = "$COMMIT" ]; then
  echo "whisper.cpp $TAG already present"; exit 0
fi
rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
git clone --quiet --depth 1 --branch "$TAG" https://github.com/ggml-org/whisper.cpp "$DEST"
ACTUAL="$(git -C "$DEST" rev-parse HEAD)"
if [ "$ACTUAL" != "$COMMIT" ]; then
  echo "whisper.cpp $TAG resolved to $ACTUAL, expected $COMMIT" >&2; exit 1
fi
echo "whisper.cpp $TAG ($COMMIT) ready"
