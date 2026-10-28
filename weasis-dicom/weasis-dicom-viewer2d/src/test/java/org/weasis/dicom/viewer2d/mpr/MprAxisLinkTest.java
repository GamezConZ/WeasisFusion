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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/** The rows of a column of the fusion layouts keep one viewport: same zoom, same pan. */
class MprAxisLinkTest {

  private static final double DELTA = 1e-9;

  /** A view taking the viewport of another one into its own model, as the real one does. */
  private static MprView view(ViewModel model) {
    MprView view = mock(MprView.class);
    when(view.getViewModel()).thenReturn(model);
    doAnswer(
            inv -> {
              ViewModel source = inv.<MprView>getArgument(0).getViewModel();
              model.setModelOffset(
                  source.getModelOffsetX(), source.getModelOffsetY(), source.getViewScale());
              return null;
            })
        .when(view)
        .followViewport(any());
    return view;
  }

  private static void assertViewport(double scale, double x, double y, ViewModel model) {
    assertAll(
        () -> assertEquals(scale, model.getViewScale(), DELTA),
        () -> assertEquals(x, model.getModelOffsetX(), DELTA),
        () -> assertEquals(y, model.getModelOffsetY(), DELTA));
  }

  @Test
  @DisplayName("Linked rows take the zoom and the pan of the primary view at once")
  void linkedRowsStartOnThePrimaryViewport() {
    ViewModel ct = new DefaultViewModel();
    ct.setModelOffset(-4.0, 9.0, 0.574);
    ViewModel nm = new DefaultViewModel();
    nm.setModelOffset(0.0, 0.0, 1.1135);
    ViewModel fused = new DefaultViewModel();
    fused.setModelOffset(0.0, 0.0, 1.1135);
    MprAxis axis = new MprAxis(Plane.AXIAL);
    axis.setMprView(view(ct));
    axis.setLinkedViews(List.of(view(nm), view(fused)));

    assertAll(
        () -> assertViewport(0.574, -4.0, 9.0, nm),
        () -> assertViewport(0.574, -4.0, 9.0, fused),
        () -> assertViewport(0.574, -4.0, 9.0, ct));
  }

  @Test
  @DisplayName(
      "A zoom or a pan of any row, or a recentering of the primary, moves the whole column")
  void viewportFollowsAcrossTheRows() {
    ViewModel ct = new DefaultViewModel();
    ViewModel fused = new DefaultViewModel();
    MprAxis axis = new MprAxis(Plane.CORONAL);
    MprView primary = view(ct);
    axis.setMprView(primary);
    axis.setLinkedViews(List.of(view(fused)));

    ct.setModelOffset(12.0, -7.0, 2.0);
    assertViewport(2.0, 12.0, -7.0, fused);

    fused.setModelOffset(3.0, 4.0, 0.5);
    assertViewport(0.5, 3.0, 4.0, ct);
    assertEquals(primary, axis.getViews().getFirst());
    assertEquals(2, axis.getViews().size());
  }

  @Test
  @DisplayName("Unlinked views (back to a standard layout) zoom and pan on their own again")
  void unlinkStopsFollowing() {
    ViewModel ct = new DefaultViewModel();
    ViewModel nm = new DefaultViewModel();
    MprAxis axis = new MprAxis(Plane.SAGITTAL);
    axis.setMprView(view(ct));
    axis.setLinkedViews(List.of(view(nm)));
    axis.setLinkedViews(List.of());

    ct.setModelOffset(5.0, 5.0, 3.0);
    assertViewport(1.0, 0.0, 0.0, nm);
    assertEquals(1, axis.getViews().size());
  }
}
