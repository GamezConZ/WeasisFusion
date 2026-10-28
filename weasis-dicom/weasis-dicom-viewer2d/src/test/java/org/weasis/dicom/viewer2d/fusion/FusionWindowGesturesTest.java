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

class FusionWindowGesturesTest {

  private static final double DELTA = 1e-9;

  private static final double BAR = 200.0;

  /** A SPECT window in raw counts, the case with no SUV to anchor the scale. */
  private static FusionWindow counts(double min, double max) {
    return new FusionWindow(min, max, 1.0, "CNTS");
  }

  private static void assertWindow(double min, double max, FusionWindow window) {
    assertAll(
        () -> assertEquals(min, window.displayMin(), DELTA),
        () -> assertEquals(max, window.displayMax(), DELTA),
        () -> assertEquals("CNTS", window.displayUnit()));
  }

  @Test
  @DisplayName("Dragging the top doubles or halves the span, between the bottom and the top limit")
  void topScalesTheSpan() {
    FusionWindow start = counts(0, 10);
    assertAll(
        () -> assertWindow(0, 20, FusionWindowGestures.moveTop(start, 128, 30)),
        () -> assertWindow(0, 5, FusionWindowGestures.moveTop(start, -128, 30)),
        () -> assertWindow(0, 30, FusionWindowGestures.moveTop(start, 5_000, 30)),
        () -> assertWindow(0, 0.1, FusionWindowGestures.moveTop(start, -50_000, 30)),
        // Without a known limit the top cannot pass where it was.
        () -> assertWindow(0, 10, FusionWindowGestures.moveTop(start, 128, Double.NaN)));
  }

  @Test
  @DisplayName("Dragging the bottom follows the pointer on the bar and keeps the top")
  void bottomFollowsThePointer() {
    FusionWindow start = counts(0, 10);
    assertAll(
        // 40 px of a 200 px bar spanning 10 counts is 2 counts of background threshold.
        () -> assertWindow(2, 10, FusionWindowGestures.moveBottom(start, 40, BAR)),
        () -> assertWindow(0, 10, FusionWindowGestures.moveBottom(start, -50, BAR)),
        () -> assertWindow(9.9, 10, FusionWindowGestures.moveBottom(start, 1_000, BAR)),
        () -> assertWindow(1.6, 10, FusionWindowGestures.moveBottom(counts(3, 10), -40, BAR)));
  }

  @Test
  @DisplayName("Dragging the body slides the window, its span kept within the limits")
  void bodySlidesTheWindow() {
    FusionWindow start = counts(2, 6);
    assertAll(
        () -> assertWindow(3, 7, FusionWindowGestures.translate(start, 50, BAR, 30)),
        () -> assertWindow(0, 4, FusionWindowGestures.translate(start, -200, BAR, 30)),
        () -> assertWindow(4, 8, FusionWindowGestures.translate(start, 200, BAR, 8)));
  }

  @Test
  @DisplayName("Window/level widens to the right and raises the level upwards")
  void windowLevelMovesWidthAndCenter() {
    FusionWindow start = counts(2, 6);
    assertAll(
        () -> assertWindow(0, 8, FusionWindowGestures.windowLevel(start, 256, 0, 256, 30)),
        () -> assertWindow(3, 7, FusionWindowGestures.windowLevel(start, 0, 64, 256, 30)),
        () -> assertWindow(3.98, 4.02, FusionWindowGestures.windowLevel(start, -1_000, 0, 256, 30)),
        // Pushed past a limit, the window stops there with its width.
        () -> assertWindow(0, 4, FusionWindowGestures.windowLevel(start, 0, -1_000, 256, 30)),
        () -> assertWindow(26, 30, FusionWindowGestures.windowLevel(start, 0, 2_000, 256, 30)));
  }

  @Test
  @DisplayName("A window/level move keeps the unit and the SUV factor of the window")
  void windowLevelKeepsTheDisplayFactor() {
    FusionWindow suv = new FusionWindow(0.0, 40_000.0, 1.0 / 4000.0, "SUVbw");
    FusionWindow moved = FusionWindowGestures.windowLevel(suv, 0, 64, 256, 30);
    assertAll(
        () -> assertEquals(2.5, moved.displayMin(), DELTA),
        () -> assertEquals(12.5, moved.displayMax(), DELTA),
        () -> assertEquals(10_000.0, moved.min(), DELTA),
        () -> assertEquals("SUVbw", moved.displayUnit()));
  }
}
