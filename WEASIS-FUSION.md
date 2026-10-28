# WeasisFusion

WeasisFusion is a build of [Weasis](https://github.com/nroduit/Weasis) for nuclear medicine reading
(SPECT/CT and PET/CT). The viewer code is the one of the `feat/nm-fusion-3x3-wl` branch; this branch
only adds the packaging and the release workflow.

## Packages

| Platform | Package | Script |
|---|---|---|
| Linux x86-64 | AppImage (deb and rpm in CI) | `weasis-distributions/script/fusion/build-appimage.sh` |
| Windows x86-64 | NSIS setup and portable folder (MSI in CI) | `weasis-distributions/script/fusion/build-windows.sh` |
| macOS aarch64 and x86-64 | `WeasisFusion.app` zip (ad hoc signed dmg in CI) | `weasis-distributions/script/fusion/build-macos.sh` |

Each package embeds a Java 25 runtime linked with `jlink` from the Temurin `jmods` of its platform
(`https://api.adoptium.net/v3/binary/latest/25/ga/<os>/<arch>/jmods/hotspot/normal/eclipse`) and
keeps only the native libraries of its platform. All three scripts run on Linux; the Windows one
needs mingw-w64 and NSIS. Shared settings (name, JVM options, modules) are in `fusion-env.sh`.

```bash
mvn -B clean install
mvn -B -f weasis-distributions clean package
Z=weasis-distributions/target/native-dist/weasis-native.zip
weasis-distributions/script/fusion/build-appimage.sh $Z <linux jmods> <appimagetool> /tmp/wf-appimage dist-packages/WeasisFusion-4.8.0-Linux-x86_64.AppImage
weasis-distributions/script/fusion/build-windows.sh $Z <windows jmods> dist-packages/WeasisFusion-4.8.0-Windows-x86_64 dist-packages/WeasisFusion-4.8.0-Windows-x86_64-Setup.exe
weasis-distributions/script/fusion/build-macos.sh $Z <macos jmods> aarch64 /tmp/wf-mac dist-packages/WeasisFusion-4.8.0-macOS-aarch64.zip
```

WeasisFusion uses the shell port 17180 (`-Dgosh.port`), so it can run beside an official Weasis.
The packages are not signed: Windows SmartScreen warns, and macOS needs
`xattr -dr com.apple.quarantine WeasisFusion.app` (or right click, Open) before the first start.

## Release

`.github/workflows/release-weasis-fusion.yml` builds every package and publishes them on the GitHub
Release of a pushed `v*-fusion` tag, or of the tag given to a manual run.
