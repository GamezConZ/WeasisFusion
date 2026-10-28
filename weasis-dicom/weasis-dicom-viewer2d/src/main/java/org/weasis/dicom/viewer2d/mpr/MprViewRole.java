/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.weasis.dicom.viewer2d.fusion.FusionDisplay;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * The plane and the fusion display of an MPR view, given by its rank among the MPR cells of the
 * layout. The standard layouts show each plane once, fused when fusion is on. The fusion layouts
 * have one column per plane and one row per display: the base image alone, the overlay alone (3x3
 * only), then both fused.
 *
 * @param plane the plane the view reslices
 * @param display what the view shows of the fused pair
 */
public record MprViewRole(Plane plane, FusionDisplay display) {

  /** Identifier of the 3x3 layout: base row, overlay row, fused row. */
  public static final String FUSION_LAYOUT_ID = "mpr3x3_fusion"; // NON-NLS

  /** Identifier of the 2x3 layout: base row, fused row. */
  public static final String FUSION_2X3_LAYOUT_ID = "mpr2x3_fusion"; // NON-NLS

  private static final List<Plane> COLUMNS = List.of(Plane.AXIAL, Plane.CORONAL, Plane.SAGITTAL);

  private static final Map<String, List<FusionDisplay>> ROWS =
      Map.of(
          FUSION_LAYOUT_ID,
          List.of(FusionDisplay.BASE, FusionDisplay.OVERLAY, FusionDisplay.FUSED),
          FUSION_2X3_LAYOUT_ID,
          List.of(FusionDisplay.BASE, FusionDisplay.FUSED));

  /** The role of the MPR view of rank {@code rank} in the layout {@code layoutId}. */
  public static MprViewRole of(String layoutId, int rank) {
    List<FusionDisplay> rows = layoutId == null ? null : ROWS.get(layoutId);
    if (rows != null) {
      int row = Math.min(rank / COLUMNS.size(), rows.size() - 1);
      return new MprViewRole(COLUMNS.get(rank % COLUMNS.size()), rows.get(row));
    }
    Plane plane = rank > 0 && rank < COLUMNS.size() ? COLUMNS.get(rank) : Plane.AXIAL;
    return new MprViewRole(plane, FusionDisplay.FUSED);
  }

  /** {@code true} for the layouts showing each plane in several rows. */
  public static boolean isFusionLayout(String layoutId) {
    return layoutId != null && ROWS.containsKey(layoutId);
  }

  /**
   * The views of each plane, in layout order: the first one of a column is the primary view its
   * {@link MprAxis} renders through, the others are linked to it.
   */
  public static <V> Map<Plane, List<V>> columns(List<V> views, Function<V, Plane> planeOf) {
    Map<Plane, List<V>> columns = new EnumMap<>(Plane.class);
    for (V view : views) {
      columns.computeIfAbsent(planeOf.apply(view), _ -> new ArrayList<>()).add(view);
    }
    return columns;
  }
}
