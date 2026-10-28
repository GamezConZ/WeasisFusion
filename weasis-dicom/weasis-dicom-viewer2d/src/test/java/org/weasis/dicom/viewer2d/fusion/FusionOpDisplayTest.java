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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.model.graphic.imp.seg.ByteLutAlpha;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;

/** What each row of the MPR fusion layout paints, given the same shared fusion settings. */
class FusionOpDisplayTest {

  private static final FusionWindow WINDOW = new FusionWindow(100.0, 900.0, 1.0, "CNTS");

  @SuppressWarnings("unchecked")
  private static final MediaSeries<DicomImageElement> SPECT = mock(MediaSeries.class);

  /** A palette going from blue (entry 0) to red (entry 255), to tell the color from gray. */
  private static final ByteLut BLUE_TO_RED = blueToRed();

  private static ByteLut blueToRed() {
    byte[][] table = new byte[3][256];
    for (int i = 0; i < 256; i++) {
      table[0][i] = (byte) (255 - i); // blue
      table[2][i] = (byte) i; // red
    }
    return new ByteLut("blue-red", table); // NON-NLS
  }

  /** A pane's op with the settings every row receives, and its own display. */
  private static FusionOp op(FusionDisplay display, LutShape shape) {
    FusionOp op = new FusionOp();
    op.setParam(FusionOp.P_FUSION_ENABLED, Boolean.TRUE);
    op.setParam(FusionOp.P_FUSION_SERIES, SPECT);
    op.setParam(FusionOp.P_FUSION_WINDOW, WINDOW);
    op.setParam(FusionOp.P_FUSION_LUT, BLUE_TO_RED);
    op.setParam(FusionOp.P_FUSION_LUT_SHAPE, shape);
    if (display != null) {
      op.setParam(FusionOp.P_FUSION_DISPLAY, display);
    }
    return op;
  }

  private static byte[][] fusedLut(LutShape shape) {
    return op(FusionDisplay.FUSED, shape).getColorScale().orElseThrow().lut().lutTable();
  }

  @Test
  @DisplayName("A view fuses unless told otherwise")
  void fusedByDefault() {
    assertEquals(FusionDisplay.FUSED, op(null, LutShape.LINEAR).getDisplay());
  }

  @Test
  @DisplayName("Only the fused row shows a color scale")
  void colorScaleOnlyOnTheFusedRow() {
    assertAll(
        () -> assertTrue(op(FusionDisplay.BASE, LutShape.LINEAR).getColorScale().isEmpty()),
        () -> assertTrue(op(FusionDisplay.OVERLAY, LutShape.LINEAR).getColorScale().isEmpty()),
        () -> assertSame(WINDOW, op(null, LutShape.LINEAR).getColorScale().orElseThrow().window()));
  }

  @Test
  @DisplayName("The fused row reads the color LUT, the overlay alone stays in opaque inverted gray")
  void overlayAloneIsInvertedGrayWhateverTheLut() {
    byte[][] fused = op(FusionDisplay.FUSED, LutShape.LINEAR).buildAlphaLut().lutTable();
    byte[][] alone = op(FusionDisplay.OVERLAY, LutShape.LINEAR).buildAlphaLut().lutTable();
    assertAll(
        () -> assertEquals((byte) 255, fused[ByteLutAlpha.B][0]),
        () -> assertEquals((byte) 255, fused[ByteLutAlpha.R][255]),
        // Fused, the lowest activity stays transparent so the anatomy shows through.
        () -> assertEquals((byte) 0, fused[ByteLutAlpha.A][0]),
        // Alone, opaque inverted gray: white at the bottom of the window, black at its top.
        () -> assertEquals((byte) 0xFF, alone[ByteLutAlpha.A][0]),
        () -> assertEquals((byte) 255, alone[ByteLutAlpha.R][0]),
        () -> assertEquals((byte) 255, alone[ByteLutAlpha.G][0]),
        () -> assertEquals((byte) 255, alone[ByteLutAlpha.B][0]),
        () -> assertEquals((byte) 55, alone[ByteLutAlpha.R][200]),
        () -> assertEquals((byte) 55, alone[ByteLutAlpha.B][200]),
        () -> assertEquals((byte) 0, alone[ByteLutAlpha.R][255]),
        () -> assertEquals((byte) 0, alone[ByteLutAlpha.G][255]),
        () -> assertEquals((byte) 0, alone[ByteLutAlpha.B][255]));
  }

  @Test
  @DisplayName("Both rows read the window through the same transfer function")
  void bothRowsShareTheTransferFunction() {
    int mid = FusionOp.shapeMap(LutShape.LOG_INV)[128];
    byte[][] alone = op(FusionDisplay.OVERLAY, LutShape.LOG_INV).buildAlphaLut().lutTable();
    assertAll(
        () -> assertEquals((byte) (255 - mid), alone[ByteLutAlpha.R][128]),
        () -> assertEquals((byte) 255, alone[ByteLutAlpha.R][0]),
        () -> assertEquals((byte) 0, alone[ByteLutAlpha.R][255]));
    for (int i = 1; i < 256; i++) {
      int previous = alone[ByteLutAlpha.R][i - 1] & 0xFF;
      assertTrue((alone[ByteLutAlpha.R][i] & 0xFF) <= previous, "decreasing at " + i);
    }
  }

  @Test
  @DisplayName("The NM row paints the window bottom and below white, its top and above black")
  void nmRowPaintsMinimumWhiteAndMaximumBlack() {
    assumeTrue(OpenCvLoader.load(), "OpenCV native library unavailable, skipping");
    // Real values: below the window, its bottom (100), inside, its top (900), above.
    ImageCV real = new ImageCV(1, 5, CvType.CV_32F);
    real.put(0, 0, new float[] {50f, 100f, 500f, 900f, 2000f});
    FusionOp op = op(FusionDisplay.OVERLAY, LutShape.LOG_INV);
    PlanarImage gray = FusionOp.normalizeOverlayImage(real, WINDOW);
    PlanarImage painted = FusionOp.overlayAlone(real, op.applyAlphaLut(gray));
    byte[] bgr = new byte[15];
    painted.toMat().get(0, 0, bgr);
    int middle = bgr[6] & 0xFF;
    assertAll(
        () -> assertEquals(CvType.CV_8UC3, painted.type()),
        () -> assertArrayEquals(new byte[] {-1, -1, -1, -1, -1, -1}, slice(bgr, 0, 6)),
        () -> assertTrue(middle > 0 && middle < 255, "gray inside the window: " + middle),
        () -> assertArrayEquals(new byte[6], slice(bgr, 9, 15)));
  }

  private static byte[] slice(byte[] values, int from, int to) {
    byte[] out = new byte[to - from];
    System.arraycopy(values, from, out, 0, out.length);
    return out;
  }

  @Test
  @DisplayName("The inverse logarithm keeps the ends and holds the low counts in the cold colors")
  void inverseLogarithmCompressesTheLowCounts() {
    int[] linear = FusionOp.shapeMap(LutShape.LINEAR);
    int[] inverseLog = FusionOp.shapeMap(LutShape.LOG_INV);
    int[] log = FusionOp.shapeMap(LutShape.LOG);
    assertAll(
        () -> assertEquals(128, linear[128]),
        () -> assertEquals(0, inverseLog[0]),
        () -> assertEquals(255, inverseLog[255]),
        () -> assertTrue(inverseLog[128] < 128, "LOG_INV is below the line"),
        () -> assertTrue(log[128] > 128, "LOG is above the line"));
    for (int i = 1; i < 256; i++) {
      assertTrue(inverseLog[i] >= inverseLog[i - 1], "monotonic at " + i);
    }
  }

  @Test
  @DisplayName("A map anchored to values (SUVbw preset, isodose) is never bent by the curve")
  void anchoredMapStaysLinear() {
    ColorMap suv =
        ColorMap.builder("suv") // NON-NLS
            .domain(ColorMapDomain.absolute("SUVbw", 0, 10)) // NON-NLS
            .stop(0, Color.BLACK)
            .stop(10, Color.RED)
            .build();
    FusionOp linear = op(FusionDisplay.FUSED, LutShape.LINEAR);
    FusionOp inverseLog = op(FusionDisplay.FUSED, LutShape.LOG_INV);
    ByteLut anchored = new ByteLut("suv", BLUE_TO_RED.lutTable(), suv); // NON-NLS
    linear.setParam(FusionOp.P_FUSION_LUT, anchored);
    inverseLog.setParam(FusionOp.P_FUSION_LUT, anchored);
    assertArrayEquals(
        linear.buildAlphaLut().lutTable()[ByteLutAlpha.R],
        inverseLog.buildAlphaLut().lutTable()[ByteLutAlpha.R]);
  }

  @Test
  @DisplayName("The transfer function moves the colors of the fused LUT, and its transparency")
  void shapeAppliesToTheFusedLut() {
    byte[][] linear = fusedLut(LutShape.LINEAR);
    byte[][] inverseLog = fusedLut(LutShape.LOG_INV);
    int mid = FusionOp.shapeMap(LutShape.LOG_INV)[128];
    assertAll(
        () -> assertEquals(linear[ByteLutAlpha.R][mid], inverseLog[ByteLutAlpha.R][128]),
        () -> assertEquals(linear[ByteLutAlpha.A][mid], inverseLog[ByteLutAlpha.A][128]),
        () -> assertEquals(FusionOp.DEFAULT_LUT_SHAPE, LutShape.LOG_INV),
        () -> assertEquals(0.5, FusionOp.DEFAULT_OVERLAY_OPACITY));
  }
}
