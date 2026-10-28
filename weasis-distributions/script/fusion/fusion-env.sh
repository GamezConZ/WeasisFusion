#!/usr/bin/env bash
# Shared settings of the WeasisFusion packages, sourced by the build-*.sh scripts.

FUSION_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_DIR=$(cd "$FUSION_DIR/../../.." && pwd)
SCRIPT_DIR="$REPO_DIR/weasis-distributions/script"

APP_NAME="WeasisFusion"
APP_ID="org.weasis.fusion"
APP_VERSION="${APP_VERSION:-$(awk -F'[<>]' '/<revision>/{print $3; exit}' "$REPO_DIR/weasis-parent/pom.xml")}"

# The JVM options of the official installers (launch-options.sh), except the shell port: 17179 is
# the one of an official Weasis that may run beside this one.
JAVA_OPTIONS=(
  --enable-native-access=ALL-UNNAMED
  -XX:MaxRAMPercentage=50
  -XX:+UseStringDeduplication
  -Dgosh.port=17180
  -Djavax.accessibility.assistive_technologies=org.weasis.launcher.EmptyAccessibilityProvider
  -Djavax.accessibility.screen_magnifier_present=false
)

# JDK modules of launch-options.sh for that platform (linux, windows, macosx), plus jdk.unsupported
# and jdk.incubator.vector.
fusion_modules() {
  # shellcheck source=../launch-options.sh
  source "$SCRIPT_DIR/launch-options.sh" "$1" >/dev/null
  echo "$JDK_MODULES,jdk.unsupported,jdk.incubator.vector"
}

# Unzips the bin-dist/weasis payload of weasis-native.zip into $2, keeping only the natives of the
# os-arch $3 (as .github/workflows/build-installer.yml does).
extract_weasis() {
  local zip=$1 dest=$2 osarch=$3 tmp
  tmp=$(mktemp -d)
  unzip -q "$zip" 'bin-dist/weasis/*' -d "$tmp"
  mkdir -p "$dest"
  cp -r "$tmp/bin-dist/weasis/." "$dest/"
  rm -rf "$tmp"
  find "$dest/bundle" -name 'weasis-opencv-core-*' ! -name "*-$osarch-*" -delete
  find "$dest/bundle" -name 'jogamp-*' ! -name "*-$osarch-*" ! -name 'jogamp-[0-9]*' -delete
}

# Links a Java runtime for the platform $4 (linux, windows, macosx) of the jmods in $1, with the
# modules $2, into $3. Native symbols are stripped for Linux only: jlink strips them with objcopy,
# which must not touch the signed binaries of the other platforms.
link_runtime() {
  local strip=--strip-java-debug-attributes
  [ "$4" = linux ] && strip=--strip-debug
  jlink --module-path "$1" --add-modules "$2" "$strip" --no-man-pages --no-header-files \
    --compress=zip-6 --output "$3"
}
