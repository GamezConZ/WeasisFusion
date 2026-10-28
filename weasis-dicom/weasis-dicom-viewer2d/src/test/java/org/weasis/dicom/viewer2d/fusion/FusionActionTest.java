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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.op.lut.ByteLut;

class FusionActionTest {

  private static ByteLut lut(String name) {
    return new ByteLut(name, new byte[3][256]);
  }

  @Test
  @DisplayName("Spectrum is found by its exact name first, then by name ignoring case")
  void spectrumIsTheDefaultPalette() {
    ByteLut hot = lut("Hot"); // NON-NLS
    ByteLut renamed = lut("NM SPECTRUM (site)"); // NON-NLS
    ByteLut spectrum = lut("Spectrum"); // NON-NLS
    assertAll(
        () ->
            assertSame(
                spectrum, FusionAction.spectrum(List.of(hot, renamed, spectrum)).orElseThrow()),
        () -> assertSame(renamed, FusionAction.spectrum(List.of(hot, renamed)).orElseThrow()),
        () -> assertTrue(FusionAction.spectrum(List.of(hot, "not a LUT")).isEmpty()));
  }
}
