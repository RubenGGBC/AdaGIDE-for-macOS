#!/usr/bin/env bash
#
# Builds AdaGIDE.app and wraps it in a .dmg using jpackage.
# Must run on macOS: jpackage produces a macOS bundle only on macOS.
#
#   ./scripts/build-dmg.sh              -> dist/AdaGIDE-<version>.dmg
#   TYPE=app-image ./scripts/build-dmg.sh -> dist/AdaGIDE.app (no disk image)
#
# Optional signing:
#   MAC_SIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)" ./scripts/build-dmg.sh
set -euo pipefail

cd "$(dirname "$0")/.."

TYPE="${TYPE:-dmg}"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"
NAME="AdaGIDE"
STAGING="build/jpackage-input"
DEST="dist"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "error: a macOS bundle can only be produced on macOS (this is $(uname -s))." >&2
  echo "       Push the branch instead and let .github/workflows/macos-dmg.yml build it." >&2
  exit 1
fi

echo "==> Building the application jar"
if [[ -x ./mvnw ]]; then ./mvnw -q clean package; else mvn -q clean package; fi

echo "==> Refreshing the icon"
if command -v iconutil >/dev/null 2>&1; then
  java packaging/GenerateIcon.java packaging/AdaGIDE.iconset
  iconutil -c icns packaging/AdaGIDE.iconset -o packaging/AdaGIDE.icns
fi

rm -rf "$STAGING" "$DEST/$NAME.app"
mkdir -p "$STAGING" "$DEST"
cp target/adagide.jar "$STAGING/"

JPACKAGE_ARGS=(
  --type "$TYPE"
  --name "$NAME"
  --app-version "$VERSION"
  --vendor "AdaGIDE for macOS"
  --copyright "AdaGIDE for macOS"
  --description "Ada GUI Integrated Development Environment for macOS"
  --input "$STAGING"
  --main-jar adagide.jar
  --main-class org.adagide.mac.AdaGide
  --icon packaging/AdaGIDE.icns
  --dest "$DEST"
  --mac-package-identifier org.adagide.mac
  --mac-package-name "$NAME"
  --file-associations packaging/ada-file-associations.properties
  --java-options -Dapple.laf.useScreenMenuBar=true
  --java-options -Dapple.awt.application.appearance=system
  --java-options -Xmx512m
)

if [[ "$TYPE" == "dmg" ]]; then
  JPACKAGE_ARGS+=(--mac-dmg-content README.md)
fi

if [[ -n "${MAC_SIGN_IDENTITY:-}" ]]; then
  JPACKAGE_ARGS+=(--mac-sign --mac-signing-key-user-name "$MAC_SIGN_IDENTITY")
  [[ -n "${MAC_SIGNING_KEYCHAIN:-}" ]] && JPACKAGE_ARGS+=(--mac-signing-keychain "$MAC_SIGNING_KEYCHAIN")
fi

echo "==> jpackage --type $TYPE"
jpackage "${JPACKAGE_ARGS[@]}"

if [[ "$TYPE" == "dmg" ]]; then
  PRODUCED="$DEST/$NAME-$VERSION.dmg"
  ARCH="$(uname -m)"
  FINAL="$DEST/$NAME-$VERSION-$ARCH.dmg"
  [[ -f "$PRODUCED" ]] && mv "$PRODUCED" "$FINAL"
  echo "==> $FINAL"
else
  echo "==> $DEST/$NAME.app"
fi
