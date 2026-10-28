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

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.fusion.FusionColorBar.Grip;

/**
 * Adjusts the overlay display window by dragging the {@link FusionColorBar} with the left button,
 * whatever action the button carries elsewhere on the view. The upper end moves the top (up widens
 * the scale), the lower end moves the bottom (the background threshold), and the body slides the
 * whole window. The press is consumed so the action bound to the button (W/L, pan…) does not run as
 * well.
 *
 * <p>The window is applied to every pane of the container that shares it and the scale sliders of
 * the fusion panel follow (see {@link FusionController#applyWindow}).
 */
public final class FusionScaleDragHandler extends MouseAdapter {

  private static final Cursor TOP_CURSOR = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
  private static final Cursor BOTTOM_CURSOR = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
  private static final Cursor BODY_CURSOR = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

  private final ViewCanvas<DicomImageElement> view;
  private FusionWindow dragStart;
  private Grip grip;
  private double barLength;
  private double topLimit;
  private int startY;
  private Grip hover;

  public FusionScaleDragHandler(ViewCanvas<DicomImageElement> view) {
    this.view = view;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.isConsumed() || !SwingUtilities.isLeftMouseButton(e)) {
      return;
    }
    Grip pressed = FusionColorBar.gripAt(view, e.getPoint());
    if (pressed == null) {
      return;
    }
    // FocusHandler runs first and has selected this view, so the window is the one shown here.
    FusionWindow window = FusionController.windowOf(view);
    if (window == null) {
      return;
    }
    e.consume();
    dragStart = window;
    grip = pressed;
    barLength = Math.max(1.0, FusionColorBar.paintedLength(view));
    topLimit = FusionController.windowTopLimit(view);
    startY = e.getY();
    view.getJComponent().setCursor(cursorOf(pressed));
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (dragStart == null) {
      return;
    }
    e.consume();
    double up = startY - (double) e.getY();
    FusionWindow window =
        switch (grip) {
          case TOP -> FusionWindowGestures.moveTop(dragStart, up, topLimit);
          case BOTTOM -> FusionWindowGestures.moveBottom(dragStart, up, barLength);
          case BODY -> FusionWindowGestures.translate(dragStart, up, barLength, topLimit);
        };
    FusionController.applyWindow(window);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (dragStart != null) {
      e.consume();
      dragStart = null;
      grip = null;
      hover = null;
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    Grip onBar = FusionColorBar.gripAt(view, e.getPoint());
    if (onBar != hover) {
      hover = onBar;
      view.getJComponent()
          .setCursor(onBar == null ? DefaultView2d.DEFAULT_CURSOR : cursorOf(onBar));
    }
  }

  private static Cursor cursorOf(Grip grip) {
    return switch (grip) {
      case TOP -> TOP_CURSOR;
      case BOTTOM -> BOTTOM_CURSOR;
      case BODY -> BODY_CURSOR;
    };
  }
}
