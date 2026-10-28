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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.viewer2d.fusion.FusionDisplay;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

class MprViewRoleTest {

  private static final String FUSION = MprViewRole.FUSION_LAYOUT_ID;

  private static List<MprViewRole> roles(String layoutId, int count) {
    List<MprViewRole> roles = new ArrayList<>();
    for (int rank = 0; rank < count; rank++) {
      roles.add(MprViewRole.of(layoutId, rank));
    }
    return roles;
  }

  @Test
  @DisplayName("The fusion layout has a column per plane and the rows CT, functional, fused")
  void fusionLayoutRowsAndColumns() {
    List<MprViewRole> roles = roles(FUSION, 9);
    List<FusionDisplay> rows =
        List.of(FusionDisplay.BASE, FusionDisplay.OVERLAY, FusionDisplay.FUSED);
    List<Plane> columns = List.of(Plane.AXIAL, Plane.CORONAL, Plane.SAGITTAL);
    for (int rank = 0; rank < roles.size(); rank++) {
      MprViewRole role = roles.get(rank);
      assertEquals(columns.get(rank % 3), role.plane(), "plane of rank " + rank);
      assertEquals(rows.get(rank / 3), role.display(), "row of rank " + rank);
    }
  }

  @Test
  @DisplayName("Each plane appears once per row, so every column is one plane in three views")
  void everyPlaneOncePerRow() {
    List<MprViewRole> roles = roles(FUSION, 9);
    for (FusionDisplay row : FusionDisplay.values()) {
      assertEquals(
          List.of(Plane.AXIAL, Plane.CORONAL, Plane.SAGITTAL),
          roles.stream().filter(r -> r.display() == row).map(MprViewRole::plane).toList());
    }
  }

  @Test
  @DisplayName("The 2x3 layout has a column per plane and the rows CT then fused")
  void twoRowLayout() {
    List<MprViewRole> roles = roles(MprViewRole.FUSION_2X3_LAYOUT_ID, 6);
    List<Plane> columns = List.of(Plane.AXIAL, Plane.CORONAL, Plane.SAGITTAL);
    for (int rank = 0; rank < roles.size(); rank++) {
      MprViewRole role = roles.get(rank);
      assertEquals(columns.get(rank % 3), role.plane(), "plane of rank " + rank);
      assertEquals(
          rank < 3 ? FusionDisplay.BASE : FusionDisplay.FUSED, role.display(), "row " + rank);
    }
    assertTrue(roles.stream().noneMatch(r -> r.display() == FusionDisplay.OVERLAY));
  }

  @Test
  @DisplayName("The views of a column are grouped by plane, the primary view first")
  void viewsAreLinkedByColumn() {
    for (String layout : List.of(FUSION, MprViewRole.FUSION_2X3_LAYOUT_ID)) {
      int rows = FUSION.equals(layout) ? 3 : 2;
      List<Integer> ranks = new ArrayList<>();
      for (int rank = 0; rank < rows * 3; rank++) {
        ranks.add(rank);
      }
      Map<Plane, List<Integer>> columns =
          MprViewRole.columns(ranks, rank -> MprViewRole.of(layout, rank).plane());
      assertAll(
          () -> assertEquals(3, columns.size()),
          // The first rank of a column is the base row: the view its axis renders through.
          () -> assertEquals(List.of(0, 3, 6).subList(0, rows), columns.get(Plane.AXIAL)),
          () -> assertEquals(List.of(1, 4, 7).subList(0, rows), columns.get(Plane.CORONAL)),
          () -> assertEquals(List.of(2, 5, 8).subList(0, rows), columns.get(Plane.SAGITTAL)));
    }
  }

  @Test
  @DisplayName("The standard layouts keep axial, coronal, sagittal, all fused")
  void standardLayoutsAreUnchanged() {
    List<MprViewRole> roles = roles("mpr1", 4); // NON-NLS
    assertAll(
        () -> assertEquals(Plane.AXIAL, roles.get(0).plane()),
        () -> assertEquals(Plane.CORONAL, roles.get(1).plane()),
        () -> assertEquals(Plane.SAGITTAL, roles.get(2).plane()),
        // A fourth MPR cell (2x2 layout) has always defaulted to axial.
        () -> assertEquals(Plane.AXIAL, roles.get(3).plane()),
        () -> assertTrue(roles.stream().allMatch(r -> r.display() == FusionDisplay.FUSED)));
  }

  @Test
  @DisplayName("Only the fusion layout is recognized as such")
  void fusionLayoutIdentifier() {
    assertAll(
        () -> assertTrue(MprViewRole.isFusionLayout(FUSION)),
        () -> assertTrue(MprViewRole.isFusionLayout(MprViewRole.FUSION_2X3_LAYOUT_ID)),
        () -> assertFalse(MprViewRole.isFusionLayout("mpr5")), // NON-NLS
        () -> assertFalse(MprViewRole.isFusionLayout(null)));
  }
}
