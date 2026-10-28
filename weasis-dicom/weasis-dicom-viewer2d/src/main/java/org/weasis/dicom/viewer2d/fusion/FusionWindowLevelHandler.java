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

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;

/**
 * The window/level mouse action of a view showing the overlay alone ({@link
 * FusionDisplay#OVERLAY}): the drag adjusts the overlay window, shared with the fused views, rather
 * than the window of the base image this view does not show. Right widens the window and up raises
 * its level (or down, following {@link WindowOp#P_INVERSE_LEVEL}), as on the base image.
 *
 * <p>It must be registered before the mouse actions it replaces, and after any handler that has a
 * better claim on the press (the MPR crosshair); the press is consumed so the window/level action
 * does not run on the base image as well.
 */
public final class FusionWindowLevelHandler extends MouseAdapter {

  /** Drag, in pixels, that shifts the level or adds to the width by the starting span. */
  private static final double PIXELS_PER_SPAN = 256.0;

  private final ViewCanvas<DicomImageElement> view;
  private FusionWindow dragStart;
  private Point start;
  private double topLimit;
  private boolean inverseLevel;

  public FusionWindowLevelHandler(ViewCanvas<DicomImageElement> view) {
    this.view = view;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.isConsumed()
        || FusionController.displayOf(view) != FusionDisplay.OVERLAY
        || !isWindowLevelButton(e)) {
      return;
    }
    FusionWindow window = FusionController.windowOf(view);
    if (window == null) {
      return;
    }
    e.consume();
    dragStart = window;
    start = e.getPoint();
    topLimit = FusionController.windowTopLimit(view);
    inverseLevel =
        view.getEventManager().getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (dragStart == null) {
      return;
    }
    e.consume();
    double right = e.getX() - (double) start.x;
    double down = e.getY() - (double) start.y;
    FusionController.applyWindow(
        FusionWindowGestures.windowLevel(
            dragStart, right, inverseLevel ? -down : down, PIXELS_PER_SPAN, topLimit));
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (dragStart != null) {
      e.consume();
      dragStart = null;
    }
  }

  private boolean isWindowLevelButton(MouseEvent e) {
    MouseActions actions = view.getEventManager().getMouseActions();
    String windowLevel = ActionW.WINLEVEL.cmd();
    return SwingUtilities.isLeftMouseButton(e) && windowLevel.equals(actions.getLeft())
        || SwingUtilities.isMiddleMouseButton(e) && windowLevel.equals(actions.getMiddle())
        || SwingUtilities.isRightMouseButton(e) && windowLevel.equals(actions.getRight());
  }
}
