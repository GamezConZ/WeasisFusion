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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.imp.seg.ByteLutAlpha;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;

/**
 * Draws the overlay color scale on a fused view: the place where the display window, derived from
 * the series or the color map, can be read. Dragging the bar vertically adjusts the window (see
 * {@link FusionScaleDragHandler}): its upper end moves the top, its lower end the bottom, and its
 * body slides the whole window. The area it was last painted in is kept on the view for that.
 *
 * <p>It is labelled in the unit the overlay actually carries — SUVbw when it could be computed, the
 * raw DICOM pixel value unit otherwise, and a percentage of the data maximum when even that is
 * unknown (see {@link FusionWindow}). Hiding the scale for non-SUV series would not stop a reader
 * from over-reading the colors, it would only hide that the overlay is windowed at all; the unit is
 * what says whether the numbers can be compared between acquisitions.
 *
 * <p>Entries the overlay paints as fully transparent are left blank, so the band of activity that
 * is deliberately not shown is visible as such instead of being mistaken for a color.
 */
public final class FusionColorBar {

  private static final int BAR_WIDTH = 19;

  /** Target number of labelled intervals; the rounded tick step decides the actual count. */
  private static final int TICKS = 4;

  /** Height kept free above and below the bar for the corner annotations. */
  private static final int RESERVED_HEIGHT = 94;

  /**
   * Below this length the bar is too short to be read or grabbed, and the view too small for it to
   * clear the corner annotations.
   */
  private static final int MIN_BAR_LENGTH = 128;

  /** Fraction of the bar, at each end, that grabs that end rather than the body. */
  private static final double END_GRIP = 0.25;

  /** Left shift applied when the base image LUT bar already occupies the right edge. */
  private static final int LUT_BAR_SHIFT = 70;

  /** Pixels around the bar that still grab it. */
  private static final int GRAB_MARGIN = 4;

  /** Client property of the view holding the area where the bar was last painted. */
  private static final String HIT_AREA = "fusion.colorBar.area"; // NON-NLS

  /** The part of the bar a drag starts from, deciding what the drag moves. */
  public enum Grip {
    TOP,
    BODY,
    BOTTOM
  }

  private FusionColorBar() {}

  /**
   * @param baseLutBarVisible {@code true} when the base image LUT bar is drawn, to sit beside it
   */
  public static void paint(
      Graphics2D g2,
      ViewCanvas<DicomImageElement> view,
      Rectangle bound,
      float midFontHeight,
      boolean baseLutBarVisible) {
    int length = barLength(bound.height);
    if (view == null || length < MIN_BAR_LENGTH) {
      return;
    }
    FusionColorScale scale =
        view.getDisplayOpManager().getNode(FusionOp.OP_NAME).orElse(null) instanceof FusionOp op
            ? op.getColorScale().orElse(null)
            : null;
    if (scale == null) {
      return;
    }

    byte[][] lut = scale.lut().lutTable();
    float x = bound.width - 30f - eastButtonWidth(view) - (baseLutBarVisible ? LUT_BAR_SHIFT : 0);
    float y = bound.height / 2f - length / 2f;
    view.getJComponent()
        .putClientProperty(
            HIT_AREA,
            new Rectangle2D.Float(
                x - GRAB_MARGIN,
                y - GRAB_MARGIN,
                BAR_WIDTH + 2f * GRAB_MARGIN,
                length + 2f * GRAB_MARGIN));

    Rectangle2D.Float rect = new Rectangle2D.Float();
    // Top row is the window maximum: walk the LUT downwards.
    for (int row = 0; row < length; row++) {
      int entry = lutEntry(length - 1 - row, length);
      if (lut[ByteLutAlpha.A][entry] == 0) {
        continue;
      }
      g2.setPaint(
          new Color(
              lut[ByteLutAlpha.R][entry] & 0xFF,
              lut[ByteLutAlpha.G][entry] & 0xFF,
              lut[ByteLutAlpha.B][entry] & 0xFF));
      rect.setRect(x, y + row, BAR_WIDTH, 1f);
      g2.fill(rect);
    }

    g2.setPaint(Color.BLACK);
    rect.setRect(x - 1f, y - 1f, BAR_WIDTH + 2f, length + 2f);
    g2.draw(rect);

    g2.setPaint(Color.WHITE);
    paintTicks(g2, scale.window(), x, y, length, midFontHeight);
  }

  /** Forgets the painted area; called before each paint, which sets it again when the bar shows. */
  public static void clearHitArea(ViewCanvas<?> view) {
    view.getJComponent().putClientProperty(HIT_AREA, null);
  }

  /** {@code true} when {@code point} (view coordinates) is on the bar as last painted. */
  public static boolean isOnBar(ViewCanvas<?> view, Point point) {
    return hitArea(view) instanceof Rectangle2D area && area.contains(point);
  }

  /**
   * The part of the bar under {@code point} (view coordinates), or {@code null} when off the bar.
   */
  public static Grip gripAt(ViewCanvas<?> view, Point point) {
    Rectangle2D area = hitArea(view);
    if (area == null || !area.contains(point)) {
      return null;
    }
    return gripAt((point.getY() - area.getY()) / area.getHeight());
  }

  /** The part of the bar at {@code fraction} of its height, from the top. */
  static Grip gripAt(double fraction) {
    if (fraction < END_GRIP) {
      return Grip.TOP;
    }
    return fraction > 1.0 - END_GRIP ? Grip.BOTTOM : Grip.BODY;
  }

  /** Length in pixels of the bar as last painted, or {@code 0} when it is not shown. */
  public static double paintedLength(ViewCanvas<?> view) {
    Rectangle2D area = hitArea(view);
    return area == null ? 0.0 : area.getHeight() - 2.0 * GRAB_MARGIN;
  }

  private static Rectangle2D hitArea(ViewCanvas<?> view) {
    return view.getJComponent().getClientProperty(HIT_AREA) instanceof Rectangle2D area
        ? area
        : null;
  }

  /**
   * One pixel per LUT entry when the view is tall enough, shorter in a small cell (a 3x3 layout) so
   * the scale stays readable there too.
   */
  static int barLength(int viewHeight) {
    return Math.min(ByteLutAlpha.CHANNEL_SIZE, viewHeight - RESERVED_HEIGHT);
  }

  /** The LUT entry painted on the bar row {@code row}, counted from the bottom of the bar. */
  static int lutEntry(int row, int length) {
    int last = ByteLutAlpha.CHANNEL_SIZE - 1;
    return length >= ByteLutAlpha.CHANNEL_SIZE
        ? row
        : (int) Math.round(row * (double) last / (length - 1));
  }

  /**
   * What the labels show: the window in its display unit, or a percentage of the window when the
   * overlay carries no unit at all — an unlabelled number would be read as a value.
   */
  private record Axis(double min, double max, String unit) {
    static Axis of(FusionWindow window) {
      return StringUtil.hasText(window.displayUnit())
          ? new Axis(window.displayMin(), window.displayMax(), window.displayUnit())
          : new Axis(0.0, 100.0, "%");
    }
  }

  private static void paintTicks(
      Graphics2D g2, FusionWindow window, float x, float y, int length, float midFontHeight) {
    Axis axis = Axis.of(window);
    double min = axis.min();
    double max = axis.max();
    float shiftY = midFontHeight / 2f - g2.getFontMetrics().getDescent();
    Line2D.Float line = new Line2D.Float();

    // Both ends are always labelled: they are the values the overlay is clipped to.
    paintTick(g2, line, min, x, y + length, shiftY);
    paintTick(g2, line, max, x, y, shiftY);

    float scale = length / (float) (max - min);
    double step = niceStep(max - min);
    for (double value = Math.ceil(min / step) * step; value < max; value += step) {
      float posY = y + length - (float) (value - min) * scale;
      // Drop the ticks that would print over a bound label.
      if (posY < y + midFontHeight || posY > y + length - midFontHeight) {
        continue;
      }
      paintTick(g2, line, value, x, posY, shiftY);
    }

    String unit = axis.unit();
    FontTools.paintFontOutline(
        g2, unit, x - g2.getFontMetrics().stringWidth(unit) - 7, y - midFontHeight / 2f);
  }

  private static void paintTick(
      Graphics2D g2, Line2D.Float line, double value, float x, float posY, float shiftY) {
    line.setLine(x - 5f, posY, x - 1f, posY);
    g2.draw(line);
    String str = DecFormatter.twoDecimal(value);
    FontTools.paintFontOutline(
        g2, str, x - g2.getFontMetrics().stringWidth(str) - 7, posY + shiftY);
  }

  /**
   * Tick step rounded to the 1-2-5 series, giving about {@link #TICKS} intervals. Dividing the
   * window into equal parts instead would label it with fractions nobody reads in these units (a 1
   * to 10 SUV scale becomes 3.25, 5.5, 7.75).
   */
  static double niceStep(double range) {
    double raw = range / TICKS;
    double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
    double normalized = raw / magnitude;
    double factor = normalized < 1.5 ? 1.0 : normalized < 3.0 ? 2.0 : normalized < 7.0 ? 5.0 : 10.0;
    return factor * magnitude;
  }

  /** Width reserved by the view buttons pinned to the right edge. */
  private static int eastButtonWidth(ViewCanvas<DicomImageElement> view) {
    int width = 0;
    for (ViewButton b : view.getViewButtons()) {
      if (b.isVisible() && b.getPosition() == GridBagConstraints.EAST) {
        width = Math.max(width, b.getIcon().getIconWidth() + 5);
      }
    }
    return width;
  }
}
