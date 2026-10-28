#!/usr/bin/env bash
# Builds the portable Windows x86-64 folder of WeasisFusion (jlink runtime + native launcher) from
# weasis-native.zip on Linux, then its NSIS installer. Needs mingw-w64 (gcc, windres) and makensis.
#
#   build-windows.sh <weasis-native.zip> <windows-x64 jmods dir> <portable dir> <setup.exe>
#
# jlink links a Windows image from the Windows jmods (the separate "jmods" artifact of Temurin 25).
set -euo pipefail
source "$(dirname "$0")/fusion-env.sh"

ZIP=$(readlink -f "$1")
JMODS=$(readlink -f "$2")
PORTABLE=$(readlink -m "$3")
SETUP=$(readlink -m "$4")
RES="$SCRIPT_DIR/resources/windows"

rm -rf "$PORTABLE"
extract_weasis "$ZIP" "$PORTABLE" windows-x86-64
link_runtime "$JMODS" "$(fusion_modules windows)" "$PORTABLE/runtime" windows
cp "$RES/Weasis.ico" "$PORTABLE/$APP_NAME.ico"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
printf '1 ICON "%s"\n' "$RES/Weasis.ico" > "$TMP/launcher.rc"
x86_64-w64-mingw32-windres "$TMP/launcher.rc" -O coff -o "$TMP/launcher-res.o"
x86_64-w64-mingw32-gcc -municode -mwindows -O2 -s -Wall \
  -DJVM_OPTIONS="L\"${JAVA_OPTIONS[*]}\"" -DAPP_TITLE="L\"$APP_NAME\"" \
  "$FUSION_DIR/fusion-launcher.c" "$TMP/launcher-res.o" -o "$PORTABLE/$APP_NAME.exe"

mkdir -p "$(dirname "$SETUP")"
makensis -V2 -DSRC="$PORTABLE" -DOUTFILE="$SETUP" -DICON="$RES/Weasis.ico" \
  -DAPPNAME="$APP_NAME" -DAPPVERSION="$APP_VERSION" "$FUSION_DIR/fusion-setup.nsi"
