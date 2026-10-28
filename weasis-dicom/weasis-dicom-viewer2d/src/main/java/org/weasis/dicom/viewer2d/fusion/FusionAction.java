/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.Feature.ToggleButtonListenerValue;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.LutShape;

/**
 * EventManager action identifiers for the PET/CT fusion controls. The command strings match the
 * {@link FusionOp} parameters so the values flow straight to the op, except {@link #WINDOW_TOP} and
 * {@link #WINDOW_BOTTOM} which move one end of the display window (see {@link FusionWindow}). The
 * series combo is typed {@code <Object>} (like {@code ActionW.PRESET}) so its list can be replaced
 * dynamically per study.
 */
public final class FusionAction {

  public static final ToggleButtonListenerValue ENABLE =
      new ToggleButtonListenerValue(
          Messages.getString("FusionTool.enable"), FusionOp.P_FUSION_ENABLED, 0, 0, null);

  public static final ComboItemListenerValue<Object> SERIES =
      new ComboItemListenerValue<>(
          Messages.getString("FusionTool.series"), FusionOp.P_FUSION_SERIES, 0, 0, null);

  public static final ComboItemListenerValue<ByteLut> LUT =
      new ComboItemListenerValue<>(
          Messages.getString("FusionTool.lut"), FusionOp.P_FUSION_LUT, 0, 0, null);

  public static final ComboItemListenerValue<LutShape> LUT_SHAPE =
      new ComboItemListenerValue<>(
          Messages.getString("FusionTool.lutShape"), FusionOp.P_FUSION_LUT_SHAPE, 0, 0, null);

  public static final SliderChangeListenerValue BASE_OPACITY =
      new SliderChangeListenerValue(
          Messages.getString("FusionTool.opacity"), FusionOp.P_OPACITY_BASE, 0, 0, null);

  public static final SliderChangeListenerValue OVERLAY_OPACITY =
      new SliderChangeListenerValue(
          Messages.getString("FusionTool.opacity"), FusionOp.P_OPACITY_OVERLAY, 0, 0, null);

  public static final SliderChangeListenerValue WINDOW_TOP =
      new SliderChangeListenerValue(
          Messages.getString("FusionTool.scaleMax"), "fusion.window.top", 0, 0, null); // NON-NLS

  public static final SliderChangeListenerValue WINDOW_BOTTOM =
      new SliderChangeListenerValue(
          Messages.getString("FusionTool.scaleMin"), "fusion.window.bottom", 0, 0, null); // NON-NLS

  /** Name of the default palette of the nuclear medicine fusion. */
  public static final String SPECTRUM = "Spectrum"; // NON-NLS

  private FusionAction() {}

  /**
   * The Spectrum palette among {@code luts}: by its exact name, else the first whose name contains
   * it whatever the case, so a site map renaming it is still found.
   */
  public static Optional<ByteLut> spectrum(Collection<?> luts) {
    List<ByteLut> candidates =
        luts.stream().filter(ByteLut.class::isInstance).map(ByteLut.class::cast).toList();
    String key = SPECTRUM.toLowerCase(Locale.ROOT);
    return candidates.stream()
        .filter(l -> SPECTRUM.equals(l.name()))
        .findFirst()
        .or(
            () ->
                candidates.stream()
                    .filter(
                        l -> l.name() != null && l.name().toLowerCase(Locale.ROOT).contains(key))
                    .findFirst());
  }
}
