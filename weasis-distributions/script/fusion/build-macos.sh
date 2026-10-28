#!/usr/bin/env bash
# Assembles WeasisFusion.app for macOS from weasis-native.zip, with a jlink runtime, and zips it
# (permissions and symbolic links kept). Runs on Linux as well as on macOS; nothing is signed here.
#
#   build-macos.sh <weasis-native.zip> <macOS jmods dir> <aarch64|x86-64> <work dir> <out.zip>
#
# The jmods are the separate "jmods" artifact of Temurin 25 for that macOS architecture.
set -euo pipefail
source "$(dirname "$0")/fusion-env.sh"

ZIP=$(readlink -f "$1")
JMODS=$(readlink -f "$2")
ARCH=$3
WORK=$(readlink -m "$4")
OUT=$(readlink -m "$5")
RES="$SCRIPT_DIR/resources/macosx"

rm -rf "$WORK"
CONTENTS="$WORK/$APP_NAME.app/Contents"
mkdir -p "$CONTENTS/MacOS" "$CONTENTS/Resources"
extract_weasis "$ZIP" "$CONTENTS/app" "macosx-$ARCH"
link_runtime "$JMODS" "$(fusion_modules macosx)" "$CONTENTS/runtime" macosx
cp "$RES/Weasis.icns" "$CONTENTS/Resources/$APP_NAME.icns"

{
  echo '#!/bin/sh'
  echo 'CONTENTS="$(cd "$(dirname "$0")/.." && pwd)"'
  echo 'APP="$CONTENTS/app"'
  echo 'exec "$CONTENTS/runtime/bin/java" -splash:"$APP/resources/images/about-round.png" \'
  echo "  -Xdock:name=$APP_NAME -Xdock:icon=\"\$CONTENTS/Resources/$APP_NAME.icns\" \\"
  echo '  -Dapple.laf.useScreenMenuBar=true -Dapple.awt.application.appearance=NSAppearanceNameDarkAqua \'
  echo "  ${JAVA_OPTIONS[*]} \\"
  echo '  -cp "$APP/weasis-launcher.jar:$APP/felix.jar" org.weasis.launcher.AppLauncher "$@"'
} > "$CONTENTS/MacOS/$APP_NAME"
chmod +x "$CONTENTS/MacOS/$APP_NAME"

# The Info.plist of the official package, with the WeasisFusion identity.
sed -e "s/DEPLOY_LAUNCHER_NAME/$APP_NAME/g" \
  -e "s/DEPLOY_ICON_FILE/$APP_NAME.icns/g" \
  -e "s/DEPLOY_BUNDLE_NAME/$APP_NAME/g" \
  -e "s/DEPLOY_BUNDLE_SHORT_VERSION/$APP_VERSION/g" \
  -e "s/DEPLOY_BUNDLE_CFBUNDLE_VERSION/$APP_VERSION/g" \
  -e "s/DEPLOY_BUNDLE_COPYRIGHT/Weasis Team and other contributors/g" \
  -e "s/<string>org.weasis.launcher<\/string>/<string>$APP_ID<\/string>/" \
  "$RES/Info.plist" > "$CONTENTS/Info.plist"
printf 'APPL????' > "$CONTENTS/PkgInfo"

mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
(cd "$WORK" && zip -q -r -y "$OUT" "$APP_NAME.app")
