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

/**
 * The overlay windows produced by the pointer gestures on the color bar and on a view showing the
 * overlay alone, kept apart from Swing so the arithmetic can be tested. Values are in the display
 * unit of the window; pointer moves are in pixels, positive upwards and rightwards.
 *
 * <p>Every gesture keeps the window open (at least {@link #MIN_SPAN} of its span), its bottom at or
 * above {@link FusionWindow#displayBottomLimit()} and its top at or below the given top limit (see
 * {@link FusionWindow#displayTopLimit}), the same bounds as the sliders of the fusion panel.
 */
public final class FusionWindowGestures {

  /** Fraction of the span a gesture always leaves between the bottom and the top. */
  static final double MIN_SPAN = 0.01;

  /**
   * Drag, in pixels, that doubles or halves the span when the top is moved: relative steps feel the
   * same on a 0 to 5 SUV scale as on a 0 to 20 one, which a fixed value per pixel would not.
   */
  static final double DOUBLING_PIXELS = 128.0;

  private FusionWindowGestures() {}

  /** Moves the top, the bottom unchanged: up widens the scale, down narrows it. */
  public static FusionWindow moveTop(FusionWindow start, double up, double topLimit) {
    double bottom = start.displayMin();
    double width = start.displayWidth();
    double top = bottom + width * Math.pow(2.0, up / DOUBLING_PIXELS);
    return start.withDisplayMax(
        Math.clamp(top, bottom + width * MIN_SPAN, highestTop(start, topLimit)));
  }

  /**
   * Moves the bottom along a bar of {@code barLength} pixels, so the end follows the pointer; the
   * top unchanged.
   */
  public static FusionWindow moveBottom(FusionWindow start, double up, double barLength) {
    double width = start.displayWidth();
    double bottom = start.displayMin() + up * width / barLength;
    return start.withDisplayMin(
        Math.clamp(bottom, start.displayBottomLimit(), start.displayMax() - width * MIN_SPAN));
  }

  /** Slides the whole window along a bar of {@code barLength} pixels, its span unchanged. */
  public static FusionWindow translate(
      FusionWindow start, double up, double barLength, double topLimit) {
    double width = start.displayWidth();
    double lowest = start.displayBottomLimit();
    double highest = Math.max(lowest, highestTop(start, topLimit) - width);
    double bottom = Math.clamp(start.displayMin() + up * width / barLength, lowest, highest);
    return start.withDisplayRange(bottom, bottom + width);
  }

  /**
   * Window/level drag, as on the base image: right widens the window, up raises its level. A move
   * of {@code pixelsPerSpan} pixels changes the width, or shifts the level, by the starting span.
   * At a limit the window stops and keeps its width, as when the body of the bar is dragged.
   */
  public static FusionWindow windowLevel(
      FusionWindow start, double right, double up, double pixelsPerSpan, double topLimit) {
    double width = start.displayWidth();
    double lowest = start.displayBottomLimit();
    double highest = highestTop(start, topLimit);
    double newWidth =
        Math.min(
            highest - lowest, Math.max(width * MIN_SPAN, width * (1.0 + right / pixelsPerSpan)));
    double center = start.displayCenter() + up * width / pixelsPerSpan;
    double bottom = Math.clamp(center - newWidth / 2.0, lowest, highest - newWidth);
    return start.withDisplayRange(bottom, bottom + newWidth);
  }

  // The slider range never cuts below the current top, so neither does a gesture.
  private static double highestTop(FusionWindow start, double topLimit) {
    return Double.isFinite(topLimit) ? Math.max(topLimit, start.displayMax()) : start.displayMax();
  }
}
