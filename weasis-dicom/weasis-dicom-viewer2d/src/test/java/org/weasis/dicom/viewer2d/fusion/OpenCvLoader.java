/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Loads the OpenCV native library built by the {@code weasis-opencv} modules of this repository,
 * for the tests that render pixels. The tests skip themselves when it is not available.
 */
final class OpenCvLoader {

  private static Boolean loaded;

  private OpenCvLoader() {}

  static synchronized boolean load() {
    if (loaded == null) {
      loaded = tryLoad();
    }
    return loaded;
  }

  private static boolean tryLoad() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String libFile =
        os.contains("win")
            ? "opencv_java.dll"
            : os.contains("mac") ? "libopencv_java.dylib" : "libopencv_java.so";
    for (Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        dir != null;
        dir = dir.getParent()) {
      Path modules = dir.resolve("weasis-opencv");
      if (Files.isDirectory(modules)) {
        return loadFirst(modules, libFile);
      }
    }
    return false;
  }

  private static boolean loadFirst(Path modules, String libFile) {
    try (Stream<Path> dirs = Files.list(modules)) {
      List<Path> candidates =
          dirs.map(d -> d.resolve("target").resolve("classes").resolve(libFile))
              .filter(Files::isRegularFile)
              .toList();
      for (Path lib : candidates) {
        try {
          System.load(lib.toString());
          return true;
        } catch (UnsatisfiedLinkError ignore) {
          // another architecture: try the next module
        }
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }
}
