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
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.viewer2d.fusion.FusionColorBar.Grip;

class FusionColorBarTest {

  private static final double DELTA = 1e-9;

  @Test
  @DisplayName("Tick steps land on the 1-2-5 series")
  void tickStepsAreRounded() {
    assertAll(
        // The default SUVbw window: labels fall on 2, 4, 6 and 8 between the 1 and 10 bounds.
        () -> assertEquals(2.0, FusionColorBar.niceStep(9.0), DELTA),
        () -> assertEquals(1.0, FusionColorBar.niceStep(5.0), DELTA),
        () -> assertEquals(0.2, FusionColorBar.niceStep(1.0), DELTA),
        () -> assertEquals(1_000.0, FusionColorBar.niceStep(4_000.0), DELTA),
        () -> assertEquals(5_000.0, FusionColorBar.niceStep(24_000.0), DELTA));
  }

  @Test
  @DisplayName("The ends of the bar grab its bounds, the body slides the window")
  void gripsFollowThePositionOnTheBar() {
    assertAll(
        () -> assertEquals(Grip.TOP, FusionColorBar.gripAt(0.0)),
        () -> assertEquals(Grip.TOP, FusionColorBar.gripAt(0.2)),
        () -> assertEquals(Grip.BODY, FusionColorBar.gripAt(0.5)),
        () -> assertEquals(Grip.BOTTOM, FusionColorBar.gripAt(0.8)),
        () -> assertEquals(Grip.BOTTOM, FusionColorBar.gripAt(1.0)));
  }

  @Test
  @DisplayName("The bar keeps one pixel per entry in a tall view and shrinks in a 3x3 cell")
  void barShrinksInSmallViews() {
    assertAll(
        () -> assertEquals(256, FusionColorBar.barLength(800)),
        () -> assertEquals(256, FusionColorBar.barLength(350)),
        () -> assertEquals(206, FusionColorBar.barLength(300)),
        // A shorter bar still spans the whole LUT, from the first to the last entry.
        () -> assertEquals(0, FusionColorBar.lutEntry(0, 206)),
        () -> assertEquals(255, FusionColorBar.lutEntry(205, 206)),
        () -> assertEquals(128, FusionColorBar.lutEntry(128, 256)));
  }
}
