#!/usr/bin/env bash
# Builds midiraja from source and installs midra to ~/bin (Git Bash / WSL).
# Usage: ./install-local.sh [--bin-dir ~/bin]

set -euo pipefail

BIN_DIR="$HOME/bin"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --bin-dir) BIN_DIR="$2"; shift 2 ;;
        --bin-dir=*) BIN_DIR="${1#*=}"; shift ;;
        *) echo "Unknown argument: $1" >&2; exit 1 ;;
    esac
done

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$PROJECT_ROOT/build/install/midrax"
TARGET_DIR="$BIN_DIR/midrax"
LAUNCHER="$BIN_DIR/midra"

echo "=== midiraja local install ==="

# 1. Build
echo ""
echo "[1/3] Building..."
cd "$PROJECT_ROOT"
./gradlew installDist -x test -x downloadFreepats -x setupFreepats -x downloadFluidR3Sf3

# 2. Copy distribution
echo ""
echo "[2/3] Installing to $TARGET_DIR..."
rm -rf "$TARGET_DIR"
cp -r "$DIST_DIR" "$TARGET_DIR"

# 3. Write launcher
echo "[3/3] Writing launcher $LAUNCHER..."
mkdir -p "$BIN_DIR"
cat > "$LAUNCHER" << 'EOF'
#!/bin/sh
MIDRAX_HOME="$(dirname "$0")/midrax"
CP=""
for jar in "$MIDRAX_HOME/lib/"*.jar; do
    CP="$CP:$jar"
done
CP="${CP#:}"
JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVA_CMD" \
  --enable-native-access=ALL-UNNAMED \
  --enable-preview \
  -cp "$CP" \
  com.fupfin.midiraja.MidirajaCommand \
  "$@"
EOF
chmod +x "$LAUNCHER"

echo ""
echo "=== Done ==="
echo "  launcher : $LAUNCHER"
echo "  dist     : $TARGET_DIR"
echo "  version  : $(midra --version 2>&1 | head -1)"
