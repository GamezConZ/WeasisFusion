#!/usr/bin/env bash
# Builds the Linux x86-64 AppImage of WeasisFusion from weasis-native.zip, with a jlink runtime.
#
#   build-appimage.sh <weasis-native.zip> <linux-x64 jmods dir> <appimagetool> <work dir> <out.AppImage>
#
# The jmods are the separate "jmods" artifact of Temurin 25 (the JDK archive ships none, JEP 493).
set -euo pipefail
source "$(dirname "$0")/fusion-env.sh"

ZIP=$(readlink -f "$1")
JMODS=$(readlink -f "$2")
APPIMAGETOOL=$(readlink -f "$3")
WORK=$(readlink -m "$4")
OUT=$(readlink -m "$5")

rm -rf "$WORK"
APPDIR="$WORK/$APP_NAME.AppDir"
extract_weasis "$ZIP" "$APPDIR/usr/lib/weasis" linux-x86-64
link_runtime "$JMODS" "$(fusion_modules linux)" "$APPDIR/usr/lib/runtime" linux

{
  echo '#!/bin/sh'
  echo 'HERE="$(dirname "$(readlink -f "$0")")"'
  echo 'W="$HERE/usr/lib/weasis"'
  echo 'exec "$HERE/usr/lib/runtime/bin/java" -splash:"$W/resources/images/about-round.png" \'
  echo "  ${JAVA_OPTIONS[*]} -Dsun.awt.disablegrab=true \\"
  echo '  -cp "$W/weasis-launcher.jar:$W/felix.jar" org.weasis.launcher.AppLauncher "$@"'
} > "$APPDIR/AppRun"
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/weasis-fusion.desktop" <<EOF
[Desktop Entry]
Version=1.0
Name=$APP_NAME
Comment=DICOM viewer with SPECT/CT and PET/CT fusion
Exec=weasis-fusion %U
StartupWMClass=org-weasis-launcher-AppLauncher
Icon=weasis-fusion
Terminal=false
Type=Application
Categories=Science;MedicalSoftware;
MimeType=application/dicom;x-scheme-handler/weasis;
EOF
cp "$SCRIPT_DIR/resources/linux/Weasis.png" "$APPDIR/weasis-fusion.png"
ln -sf weasis-fusion.png "$APPDIR/.DirIcon"

mkdir -p "$(dirname "$OUT")"
# --appimage-extract-and-run: works where FUSE is not available.
ARCH=x86_64 "$APPIMAGETOOL" --appimage-extract-and-run --no-appstream "$APPDIR" "$OUT"
