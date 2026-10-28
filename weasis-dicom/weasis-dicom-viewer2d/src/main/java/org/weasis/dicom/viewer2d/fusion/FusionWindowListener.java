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

import javax.swing.BoundedRangeModel;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.util.StringUtil;

/**
 * Slider moving one end of the overlay display window, in the unit the {@link FusionColorBar} reads
 * (SUVbw when known). The top slider sets the scale maximum; the bottom slider sets the scale
 * minimum, the background threshold of SPECT reading, which nuclear medicine otherwise reads from
 * zero. Dragging the color bar and the window/level action on the overlay alone move the same
 * window, and both sliders follow.
 */
public class FusionWindowListener extends SliderChangeListener {

  /** The end of the window a slider moves. */
  public enum Bound {
    BOTTOM,
    TOP
  }

  private static final int SLIDER_RANGE = 1000;

  private final Bound bound;
  private String unit = StringUtil.EMPTY_STRING;
  private boolean applying;

  public FusionWindowListener(Bound bound) {
    super(
        bound == Bound.TOP ? FusionAction.WINDOW_TOP : FusionAction.WINDOW_BOTTOM,
        0.0,
        1.0,
        bound == Bound.TOP ? 1.0 : 0.0,
        true,
        1.0,
        SLIDER_RANGE);
    this.bound = bound;
  }

  @Override
  public void stateChanged(BoundedRangeModel model) {
    // The other slider follows the new window; this one is the source and is left alone.
    applying = true;
    try {
      double value = toModelValue(model.getValue());
      if (bound == Bound.TOP) {
        FusionController.applyWindowTop(value);
      } else {
        FusionController.applyWindowBottom(value);
      }
    } finally {
      applying = false;
    }
  }

  @Override
  public void updateSliderProperties(JSliderW slider) {
    String title = getActionW().getTitle() + StringUtil.COLON_AND_SPACE + getValueToDisplay();
    SliderChangeListener.updateSliderProperties(
        slider, StringUtil.hasText(unit) ? title + StringUtil.SPACE + unit : title);
  }

  /**
   * Follows {@code window} without applying anything, both in the display unit: the top ranges from
   * just above the bottom to {@code topLimit}, the bottom from {@link
   * FusionWindow#displayBottomLimit()} to just below the top.
   */
  public void sync(FusionWindow window, double topLimit) {
    if (applying || window == null || !Double.isFinite(topLimit)) {
      return;
    }
    unit = window.displayUnit();
    if (bound == Bound.TOP) {
      double bottom = window.displayMin();
      double top = Math.max(topLimit, window.displayMax());
      setRealMinMaxValue(
          bottom + (top - bottom) * FusionWindowGestures.MIN_SPAN, top, window.displayMax(), false);
    } else {
      double lowest = window.displayBottomLimit();
      double top = window.displayMax();
      setRealMinMaxValue(
          lowest, top - (top - lowest) * FusionWindowGestures.MIN_SPAN, window.displayMin(), false);
    }
  }
}
