#!/usr/bin/env bash
# scripts/upload-midrax.sh
# Builds the cross-platform midrax JAR locally and uploads it to the GitHub release.
#
# Usage:
#   ./scripts/upload-midrax.sh           # uploads to the release matching the current git tag
#   ./scripts/upload-midrax.sh v0.1.0    # uploads to the specified release tag
#
# Requirements: gh CLI (https://cli.github.com/) must be installed and authenticated.

set -euo pipefail

cd "$(dirname "$0")/.."

# Determine release tag
if [ $# -ge 1 ]; then
    TAG="$1"
else
    TAG="$(git describe --exact-match --tags HEAD 2>/dev/null || true)"
    if [ -z "$TAG" ]; then
        echo "Error: HEAD is not on a tag. Pass the tag explicitly: $0 v0.1.0" >&2
        exit 1
    fi
fi

VERSION="${TAG#v}"
ZIP_NAME="midrax-v${VERSION}.zip"

echo "Building midrax JAR (version ${VERSION})..."
./gradlew distZip -x buildAdlMidiLib -x buildOpnMidiLib -x buildMiniaudioLib -q

mv build/distributions/midiraja-*.zip "${ZIP_NAME}"
echo "Built: ${ZIP_NAME}"

echo "Uploading to GitHub release ${TAG}..."
gh release upload "${TAG}" "${ZIP_NAME}" --clobber

echo "Done: ${ZIP_NAME} uploaded to release ${TAG}"
