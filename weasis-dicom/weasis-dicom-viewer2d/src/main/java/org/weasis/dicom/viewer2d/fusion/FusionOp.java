/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.LookupTableUtils;
import org.joml.Vector3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.image.AbstractOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.ui.model.graphic.imp.seg.ByteLutAlpha;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

/**
 * Image operation that overlays a PET (or other functional) image onto the current CT/MR image and
 * blends them with an intensity-modulated alpha.
 *
 * <p>Values are mapped to the LUT through a series-wide {@link FusionWindow}, then colorised
 * through a {@link ByteLut} into a per-pixel ABGR overlay: background stays fully transparent while
 * everything above it is blended at the overlay opacity, so the palette alone carries the value.
 *
 * <p>Two geometries are supported for placing the PET data onto the displayed plane:
 *
 * <ol>
 *   <li><b>Volume reslice</b> (preferred, set via {@link #P_FUSION_VOLUME}): the PET volume is
 *       resampled at the true 3D position of the displayed plane ({@link FusionVolumeResampler}).
 *       This stays correct for MPR/oblique reslices.
 *   <li><b>Single-slice fallback</b> (no volume available): the nearest native PET slice is found
 *       ({@link FusionSliceMatcher}) and 2D-affine aligned ({@link FusionRegistration}).
 * </ol>
 *
 * <p>Supported parameters:
 *
 * <ul>
 *   <li>{@link #P_FUSION_ENABLED} - Enable/disable fusion (Boolean)
 *   <li>{@link #P_FUSION_SERIES} - The PET MediaSeries (MediaSeries)
 *   <li>{@link #P_FUSION_VOLUME} - The resampled PET Volume, optional (Volume)
 *   <li>{@link #P_FUSION_LUT} - The PET color lookup table (ByteLut)
 *   <li>{@link #P_FUSION_WINDOW} - The PET display window (FusionWindow)
 *   <li>{@link #P_FUSION_LUT_SHAPE} - The transfer function over the window (LutShape)
 *   <li>{@link #P_OPACITY_BASE} - CT opacity 0.0–1.0 (Double)
 *   <li>{@link #P_OPACITY_OVERLAY} - PET opacity 0.0–1.0 (Double)
 *   <li>{@link #P_BASE_IMAGE} - The current CT DicomImageElement (DicomImageElement)
 *   <li>{@link #P_FUSION_DISPLAY} - What the view shows of the pair, fused by default
 *       (FusionDisplay)
 * </ul>
 */
public class FusionOp extends AbstractOp {

  public static final String OP_NAME = "fusion.pet.ct";

  /** Enable/disable fusion. */
  public static final String P_FUSION_ENABLED = "fusion.enabled";

  /** The PET series to fuse. */
  public static final String P_FUSION_SERIES = "fusion.series";

  /** The rectified PET volume to reslice (optional; enables MPR-correct fusion). */
  public static final String P_FUSION_VOLUME = "fusion.volume";

  /** The PET color LUT. */
  public static final String P_FUSION_LUT = "fusion.lut";

  /** The PET display window ({@link FusionWindow}, in modality-LUT values). */
  public static final String P_FUSION_WINDOW = "fusion.window";

  /** The transfer function from the window to the LUT entries ({@link LutShape}). */
  public static final String P_FUSION_LUT_SHAPE = "fusion.lut.shape";

  /** CT/MR opacity (0.0 to 1.0). */
  public static final String P_OPACITY_BASE = "fusion.opacity.base";

  /** PET opacity (0.0 to 1.0). */
  public static final String P_OPACITY_OVERLAY = "fusion.opacity.overlay";

  /** The current base (CT/MR) image element. */
  public static final String P_BASE_IMAGE = "fusion.base.image";

  /** What the view shows of the pair ({@link FusionDisplay}); set per view, never shared. */
  public static final String P_FUSION_DISPLAY = "fusion.display";

  /** Default {@link #P_OPACITY_BASE}: the anatomy is shown untouched where the overlay is cold. */
  public static final double DEFAULT_BASE_OPACITY = 1.0;

  /**
   * Default {@link #P_OPACITY_OVERLAY}. An even split is what PET/CT readers are used to: the LUT
   * bottom is black, so the overlay dims the anatomy by this much wherever uptake is low, and going
   * higher darkens the CT more than it reveals.
   */
  public static final double DEFAULT_OVERLAY_OPACITY = 0.5;

  /**
   * Default {@link #P_FUSION_LUT_SHAPE}: the inverse logarithm (exponential) holds the low counts
   * of a SPECT, where scatter and background sit, in the cold end of the palette and spends it on
   * the uptake.
   */
  public static final LutShape DEFAULT_LUT_SHAPE = LutShape.LOG_INV;

  private static final int LUT_SIZE = 256;

  /** Overlay entries up to this LUT index are painted fully transparent (air, outside the body). */
  private static final int ALPHA_TRANSPARENT_BELOW = LUT_SIZE / 100;

  /**
   * LUT index from which the overlay reaches the full overlay opacity. Alpha must saturate early:
   * the color LUT already encodes the value, so low uptake belongs on screen as the dark end of the
   * palette. Fading it out as well — the entry is then dark <em>and</em> transparent — is what
   * makes everything but the hottest voxels disappear.
   */
  private static final int ALPHA_OPAQUE_FROM = LUT_SIZE / 10;

  /** Identity gray BGR LUT used when no color LUT is selected. */
  private static final byte[][] GRAY_LUT = buildGrayLut(false);

  /**
   * Inverted gray of the overlay shown alone, as nuclear medicine reads it (like MONOCHROME1, PS3.3
   * C.7.6.3.1.2): white at and below the bottom of the window, black at and above its top.
   */
  private static final byte[][] INVERTED_GRAY_LUT = buildGrayLut(true);

  private static final int VOLUME_OVERLAY_CACHE_SIZE = 8;

  /**
   * Slice-path cache keyed by the matched PET slice (strongly held by the series). Color is baked
   * in; opacity is applied at composite time, so the cache survives opacity changes.
   */
  private final transient Map<DicomImageElement, PlanarImage> sliceOverlayCache =
      new WeakHashMap<>();

  /**
   * Volume-path cache keyed by the displayed plane geometry. MPR reuses a single {@link
   * DicomImageElement} per axis and only mutates its geometry between reslices, so the overlay must
   * be keyed by the plane (which has value-based equality) rather than the element identity —
   * otherwise the first plane's overlay would be reused for every reslice. Bounded LRU because the
   * {@link GeometryOfSlice} keys are not retained anywhere else.
   */
  private final transient Map<GeometryOfSlice, PlanarImage> volumeOverlayCache =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GeometryOfSlice, PlanarImage> eldest) {
          return size() > VOLUME_OVERLAY_CACHE_SIZE;
        }
      };

  public FusionOp() {
    setName(OP_NAME);
  }

  public FusionOp(FusionOp op) {
    super(op);
  }

  @Override
  public FusionOp copy() {
    return new FusionOp(this);
  }

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    OpEvent type = event.eventType();
    if (OpEvent.IMAGE_CHANGE.equals(type)) {
      setParam(P_BASE_IMAGE, event.image());
    } else if (OpEvent.RESET_DISPLAY.equals(type)) {
      // Reset returns to the plain base image: fusion is an opt-in overlay, so turn it off and
      // release the overlay state. The same volume instance is shared across the MPR panes, so only
      // drop the reference here; GC reclaims it once every pane has released it.
      setParam(P_FUSION_ENABLED, Boolean.FALSE);
      setParam(P_BASE_IMAGE, null);
      setParam(P_FUSION_VOLUME, null);
      setParam(P_FUSION_WINDOW, null);
      clearCache();
    } else if (OpEvent.SERIES_CHANGE.equals(type)) {
      setParam(P_BASE_IMAGE, null);
      clearCache();
      // The overlay is tied to the base series. Keep it (and its volume) only while the selected
      // functional series is still compatible with the new base series; otherwise the fusion is
      // meaningless, so disable it and release the volume.
      if (!remainsCompatibleWith(event.series())) {
        setParam(P_FUSION_ENABLED, Boolean.FALSE);
        setParam(P_FUSION_SERIES, null);
        setParam(P_FUSION_VOLUME, null);
        setParam(P_FUSION_WINDOW, null);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private boolean remainsCompatibleWith(MediaSeries<? extends ImageElement> newBase) {
    Boolean enabled = getParam(P_FUSION_ENABLED, Boolean.class);
    if (enabled == null || !enabled) {
      return true; // nothing active to invalidate
    }
    if (!(params.get(P_FUSION_SERIES) instanceof MediaSeries<?> overlay)
        || !(newBase instanceof MediaSeries<?> base)) {
      return false;
    }
    return FusionCompatibility.isCompatible(
        (MediaSeries<DicomImageElement>) base, (MediaSeries<DicomImageElement>) overlay);
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    PlanarImage result = fusionProcess(source);
    params.put(Param.OUTPUT_IMG, result);
  }

  /** What this view shows of the pair; {@link FusionDisplay#FUSED} unless set otherwise. */
  public FusionDisplay getDisplay() {
    return getParam(P_FUSION_DISPLAY, FusionDisplay.class, FusionDisplay.FUSED);
  }

  @SuppressWarnings("unchecked")
  private PlanarImage fusionProcess(PlanarImage baseSource) {
    FusionDisplay display = getDisplay();
    if (display == FusionDisplay.BASE) {
      return baseSource;
    }
    boolean overlayOnly = display == FusionDisplay.OVERLAY;
    Boolean enabled = getParam(P_FUSION_ENABLED, Boolean.class);
    MediaSeries<DicomImageElement> overlaySeries =
        (MediaSeries<DicomImageElement>) params.get(P_FUSION_SERIES);
    DicomImageElement baseImage = getParam(P_BASE_IMAGE, DicomImageElement.class);
    if (enabled == null || !enabled || overlaySeries == null || baseImage == null) {
      // Showing the anatomy where the functional series is expected would be misread as uptake.
      return overlayOnly ? blank(baseSource) : baseSource;
    }

    if (overlayOnly) {
      PlanarImage overlayImg = getOverlay(baseImage, overlaySeries, baseSource);
      return overlayImg == null ? blank(baseSource) : overlayAlone(baseSource, overlayImg);
    }

    double overlayOpacity = getParam(P_OPACITY_OVERLAY, Double.class, DEFAULT_OVERLAY_OPACITY);
    if (overlayOpacity <= 0.0) {
      return baseSource;
    }
    double baseOpacity = getParam(P_OPACITY_BASE, Double.class, DEFAULT_BASE_OPACITY);

    PlanarImage overlayImg = getOverlay(baseImage, overlaySeries, baseSource);
    if (overlayImg == null) {
      return baseSource;
    }
    return compositeOver(baseSource, overlayImg, baseOpacity, overlayOpacity);
  }

  /** Builds (or retrieves from cache) the ABGR PET overlay aligned to the CT pixel grid. */
  private PlanarImage getOverlay(
      DicomImageElement baseImage,
      MediaSeries<DicomImageElement> overlaySeries,
      PlanarImage baseSource) {
    Volume<?, ?> volume = getParam(P_FUSION_VOLUME, Volume.class);
    if (volume != null && !volume.isBasic()) {
      return getVolumeOverlay(volume, baseImage, overlaySeries, baseSource);
    }
    return getSliceOverlay(baseImage, overlaySeries, baseSource);
  }

  /** Volume path: reslice the PET volume on the displayed plane, then colorise. */
  private PlanarImage getVolumeOverlay(
      Volume<?, ?> volume,
      DicomImageElement baseImage,
      MediaSeries<DicomImageElement> overlaySeries,
      PlanarImage baseSource) {
    GeometryOfSlice plane = baseImage.getSliceGeometry();
    if (plane == null) {
      return null;
    }
    PlanarImage cached = volumeOverlayCache.get(plane);
    if (cached != null) {
      return cached;
    }
    FusionWindow window = resolveWindow(overlaySeries);
    if (window == null) {
      return null;
    }
    PlanarImage overlayGray =
        FusionVolumeResampler.resampleToGray(
            volume, plane, baseSource.width(), baseSource.height(), window.min(), window.max());
    if (overlayGray == null) {
      return null;
    }
    PlanarImage overlay = applyAlphaLut(overlayGray);
    volumeOverlayCache.put(plane, overlay);
    return overlay;
  }

  /** Fallback path: nearest native PET slice, colorised and 2D-affine aligned to the CT grid. */
  private PlanarImage getSliceOverlay(
      DicomImageElement baseImage,
      MediaSeries<DicomImageElement> overlaySeries,
      PlanarImage baseSource) {
    DicomImageElement overlayImage =
        FusionSliceMatcher.findMatchingSlice(baseImage, overlaySeries, null, null);
    if (overlayImage == null) {
      return null;
    }
    PlanarImage cached = sliceOverlayCache.get(overlayImage);
    if (cached != null) {
      return cached;
    }
    // The window is expressed in modality-LUT values, so the slice must be rescaled the same way
    // the volume path already is; getImage() would yield raw stored values.
    PlanarImage overlayReal = overlayImage.getModalityLutImage(null, null);
    FusionWindow window = resolveWindow(overlaySeries);
    if (overlayReal == null || window == null) {
      return null;
    }
    PlanarImage overlayGray = normalizeOverlayImage(overlayReal, window);
    PlanarImage colorized = applyAlphaLut(overlayGray);
    PlanarImage aligned =
        FusionRegistration.alignOverlayToBase(overlayImage, colorized, baseImage, baseSource);
    sliceOverlayCache.put(overlayImage, aligned);
    return aligned;
  }

  /**
   * The window set by the fusion controls, falling back to the series default so the overlay is
   * still scaled consistently before the controls have pushed one (e.g. a restored view state).
   */
  private FusionWindow resolveWindow(MediaSeries<DicomImageElement> overlaySeries) {
    FusionWindow window = getParam(P_FUSION_WINDOW, FusionWindow.class);
    return window == null ? FusionWindow.fromSlice(overlaySeries) : window;
  }

  /** Maps the overlay's real values to 8-bit grayscale through the series-wide window. */
  static PlanarImage normalizeOverlayImage(PlanarImage overlayReal, FusionWindow window) {
    double slope = 255.0 / (window.max() - window.min());
    ImageCV result = new ImageCV();
    overlayReal.toMat().convertTo(result, CvType.CV_8U, slope, -slope * window.min());
    return result;
  }

  /**
   * The ABGR LUT the windowed intensity is looked up in: the color LUT (see {@link #colorLut()})
   * read through the transfer function of {@link #P_FUSION_LUT_SHAPE}. Composing them keeps a
   * single lookup per pixel, and the color bar, which paints this LUT against a linear value axis,
   * shows where the curve puts the colors.
   */
  ByteLutAlpha buildAlphaLut() {
    return shaped(colorLut(), transferFunction());
  }

  /**
   * The transfer function in force, the same for the fused overlay and the overlay alone: linear
   * under a map anchored to physical values (an SUVbw preset, the isodose bands of a dose grid),
   * whose colors stand for given values that a curve would move.
   */
  private LutShape transferFunction() {
    ByteLut lut = getParam(P_FUSION_LUT, ByteLut.class);
    ColorMap map = lut == null ? null : lut.source();
    if (map != null && !map.domain().isRelative()) {
      return LutShape.LINEAR;
    }
    return getParam(P_FUSION_LUT_SHAPE, LutShape.class, DEFAULT_LUT_SHAPE);
  }

  /**
   * Color from the selected {@link ByteLut}, alpha reaching full opacity by {@link
   * #ALPHA_OPAQUE_FROM}. The global PET opacity is applied later at composite time, so it is not
   * baked in. Shown alone the overlay is opaque inverted gray whatever the LUT: the palette belongs
   * to the fusion, and there is no anatomy to keep visible under its lowest entries.
   */
  private ByteLutAlpha colorLut() {
    if (getDisplay() == FusionDisplay.OVERLAY) {
      byte[] alpha = new byte[LUT_SIZE];
      Arrays.fill(alpha, (byte) 0xFF);
      byte[][] gray = INVERTED_GRAY_LUT;
      return new ByteLutAlpha(OP_NAME, new byte[][] {alpha, gray[0], gray[1], gray[2]});
    }
    ByteLut lut = getParam(P_FUSION_LUT, ByteLut.class);
    ColorMap map = lut == null ? null : lut.source();
    if (map != null && map.hasAlpha()) {
      // A transfer map carries its own alpha curve over the window: no built-in ramp.
      return new ByteLutAlpha(OP_NAME, ColorMapCompiler.toAbgr(map, LUT_SIZE));
    }
    byte[][] bgr = lut != null && lut.lutTable() != null ? lut.lutTable() : GRAY_LUT;
    return ByteLutAlpha.fromColorLut(
        OP_NAME, bgr, 1.0f, ALPHA_TRANSPARENT_BELOW, ALPHA_OPAQUE_FROM);
  }

  /**
   * {@code lut} read through {@code shape}: entry {@code i} takes the entry the curve maps it to.
   */
  static ByteLutAlpha shaped(ByteLutAlpha lut, LutShape shape) {
    int[] map = shapeMap(shape);
    byte[][] src = lut.lutTable();
    byte[][] dst = new byte[src.length][LUT_SIZE];
    for (int c = 0; c < src.length; c++) {
      for (int i = 0; i < LUT_SIZE; i++) {
        dst[c][i] = src[c][map[i]];
      }
    }
    return new ByteLutAlpha(lut.name(), dst);
  }

  /**
   * The LUT entry each windowed intensity takes under {@code shape}, with the curves of the base
   * image window ({@link LookupTableUtils#createVoiLut}) so a shape reads the same on both layers.
   * The curve is stretched to its ends, so the bottom and the top of the window keep the first and
   * the last entry of the palette. Identity for a linear or missing shape.
   */
  static int[] shapeMap(LutShape shape) {
    int[] map = new int[LUT_SIZE];
    LookupTableCV curve =
        shape == null || LutShape.LINEAR.equals(shape)
            ? null
            : LookupTableUtils.createVoiLut(
                shape, LUT_SIZE, LUT_SIZE / 2.0, 0, LUT_SIZE - 1, 8, false, false);
    int last = LUT_SIZE - 1;
    if (curve == null || curve.lookup(0, last) == curve.lookup(0, 0)) {
      for (int i = 0; i < LUT_SIZE; i++) {
        map[i] = i;
      }
      return map;
    }
    double low = curve.lookup(0, 0);
    double span = curve.lookup(0, last) - low;
    for (int i = 0; i < LUT_SIZE; i++) {
      map[i] = (int) Math.clamp(Math.round((curve.lookup(0, i) - low) * last / span), 0, last);
    }
    return map;
  }

  /** Colorizes a grayscale PET image into an ABGR overlay. */
  PlanarImage applyAlphaLut(PlanarImage overlayGray) {
    ByteLutAlpha abgrLut = buildAlphaLut();

    Mat src = overlayGray.toMat();
    Mat src4 = new Mat();
    Mat lutMat = abgrLut.toLutMat();
    try {
      // Replicate the gray mask into all 4 channels so every output channel (alpha included) is
      // looked up from the same intensity, then map to ABGR in one native call. GRAY2BGRA would
      // instead set the 4th channel to a constant 255, pinning the red output to red[255].
      Core.merge(List.of(src, src, src, src), src4);
      ImageCV dst = new ImageCV();
      Core.LUT(src4, lutMat, dst);
      return dst;
    } finally {
      lutMat.release();
      src4.release();
    }
  }

  /**
   * Composites an ABGR PET overlay over the CT image: {@code result = baseOpacity * CT * (1 - a) +
   * PET * a}, where {@code a = (overlayAlpha / 255) * overlayOpacity}.
   */
  private static PlanarImage compositeOver(
      PlanarImage baseSource, PlanarImage overlayImg, double baseOpacity, double overlayOpacity) {
    Mat baseBgr = toBgr8U(baseSource.toMat());
    Mat overlay = overlayImg.toMat();
    if (overlay.width() != baseBgr.width() || overlay.height() != baseBgr.height()) {
      Mat resized = new Mat();
      Imgproc.resize(overlay, resized, baseBgr.size(), 0, 0, Imgproc.INTER_LINEAR);
      overlay = resized;
    }

    List<Mat> ch = new java.util.ArrayList<>(4);
    Core.split(overlay, ch); // A, B, G, R

    Mat alpha = new Mat();
    ch.getFirst()
        .convertTo(alpha, CvType.CV_32F, overlayOpacity / 255.0); // a in [0, overlayOpacity]
    Mat alpha3 = new Mat();
    Core.merge(List.of(alpha, alpha, alpha), alpha3);

    Mat overlayBgr = new Mat();
    Core.merge(List.of(ch.get(1), ch.get(2), ch.get(3)), overlayBgr);
    Mat overlayF = new Mat();
    overlayBgr.convertTo(overlayF, CvType.CV_32FC3);
    Mat baseF = new Mat();
    baseBgr.convertTo(baseF, CvType.CV_32FC3);

    Mat invAlpha = new Mat();
    Core.multiply(alpha3, Scalar.all(-1.0), invAlpha);
    Core.add(invAlpha, Scalar.all(1.0), invAlpha); // 1 - a

    Mat baseTerm = new Mat();
    Core.multiply(baseF, invAlpha, baseTerm);
    if (baseOpacity != 1.0) {
      Core.multiply(baseTerm, Scalar.all(baseOpacity), baseTerm);
    }
    Mat overlayTerm = new Mat();
    Core.multiply(overlayF, alpha3, overlayTerm);
    Mat sum = new Mat();
    Core.add(baseTerm, overlayTerm, sum);

    ImageCV result = new ImageCV();
    sum.convertTo(result, CvType.CV_8UC3);

    releaseAll(alpha, alpha3, overlayBgr, overlayF, baseF, invAlpha, baseTerm, overlayTerm, sum);
    ch.forEach(Mat::release);
    return result;
  }

  /** The colorized overlay painted alone, on a black image of the size of {@code baseSource}. */
  static PlanarImage overlayAlone(PlanarImage baseSource, PlanarImage overlayImg) {
    // Over black, so a transfer map keeps its own transparency; a plain LUT is opaque anyway.
    ImageCV black = blank(baseSource);
    try {
      return compositeOver(black, overlayImg, 0.0, 1.0);
    } finally {
      black.release();
    }
  }

  /** Black BGR image of the size of {@code baseSource}: the background of the overlay alone. */
  private static ImageCV blank(PlanarImage baseSource) {
    return new ImageCV(baseSource.height(), baseSource.width(), CvType.CV_8UC3, Scalar.all(0));
  }

  /** Ensures an 8-bit, 3-channel BGR matrix for compositing. */
  private static Mat toBgr8U(Mat src) {
    Mat m = src;
    if (m.depth() != CvType.CV_8U) {
      Mat conv = new Mat();
      m.convertTo(conv, CvType.CV_8U);
      m = conv;
    }
    if (m.channels() == 1) {
      Mat bgr = new Mat();
      Imgproc.cvtColor(m, bgr, Imgproc.COLOR_GRAY2BGR);
      return bgr;
    }
    if (m.channels() == 4) {
      Mat bgr = new Mat();
      Imgproc.cvtColor(m, bgr, Imgproc.COLOR_BGRA2BGR);
      return bgr;
    }
    return m;
  }

  private static void releaseAll(Mat... mats) {
    for (Mat mat : mats) {
      mat.release();
    }
  }

  private static byte[][] buildGrayLut(boolean inverted) {
    byte[][] lut = new byte[3][LUT_SIZE];
    for (int i = 0; i < LUT_SIZE; i++) {
      byte v = (byte) (inverted ? LUT_SIZE - 1 - i : i);
      lut[0][i] = v;
      lut[1][i] = v;
      lut[2][i] = v;
    }
    return lut;
  }

  /**
   * Builds a measurable layer exposing the fused PET values on the CT pixel grid, so an area
   * measurement on the CT image can also report PET SUV statistics. Returns empty when fusion is
   * disabled or the PET data cannot be aligned. Computed on demand (no caching) since region
   * statistics are only requested on measurement release.
   *
   * <p>Unlike the display overlay, statistics favor fidelity over MPR-correctness: when the
   * displayed plane is coplanar with a native PET slice (the usual axial PET/CT case) it samples
   * that slice's actual voxels, avoiding the smoothing introduced by the rectified fusion volume —
   * which would otherwise lower SUVmax. The volume is used only as a fallback for oblique/MPR
   * planes that have no matching native slice.
   */
  @SuppressWarnings("unchecked")
  public Optional<MeasurableLayer> getStatsLayer(
      DicomImageElement baseImage, PlanarImage baseSource) {
    Boolean enabled = getParam(P_FUSION_ENABLED, Boolean.class);
    if (enabled == null || !enabled || getDisplay() == FusionDisplay.BASE) {
      return Optional.empty();
    }
    MediaSeries<DicomImageElement> overlaySeries =
        (MediaSeries<DicomImageElement>) params.get(P_FUSION_SERIES);
    if (overlaySeries == null || baseImage == null || baseSource == null) {
      return Optional.empty();
    }
    // Prefix stats with the overlay's real modality (PT for PET, NM for SPECT, …) so the panel is
    // correct for non-PET overlays.
    String modality = overlayModalityLabel(overlaySeries);

    // Preferred: measure the native overlay slice directly. The ROI is mapped from the displayed
    // image into overlay pixel space, so statistics are computed on the untouched overlay voxels —
    // no resampling, hence no interpolation loss on the max value. Valid when the matched overlay
    // slice is coplanar with the displayed plane (the usual axial PET/CT case).
    DicomImageElement overlaySlice =
        FusionSliceMatcher.findMatchingSlice(baseImage, overlaySeries, null, null);
    if (overlaySlice != null) {
      AffineTransform baseToOverlay = baseToOverlayTransform(baseImage, overlaySlice);
      PlanarImage overlayStored = baseToOverlay == null ? null : overlaySlice.getImage();
      if (overlayStored != null) {
        return Optional.of(
            new FusionMeasurableLayer(overlayStored, overlaySlice, baseToOverlay, false, modality));
      }
    }

    // Fallback: reslice the rectified volume for oblique/MPR planes with no coplanar native slice.
    Volume<?, ?> volume = getParam(P_FUSION_VOLUME, Volume.class);
    if (volume != null && !volume.isBasic()) {
      GeometryOfSlice plane = baseImage.getSliceGeometry();
      DicomImageElement refOverlay = overlaySeries.getMedia(MEDIA_POSITION.MIDDLE, null, null);
      if (plane != null && refOverlay != null) {
        PlanarImage values =
            FusionVolumeResampler.resampleToValue(
                volume, plane, baseSource.width(), baseSource.height());
        if (values != null) {
          return Optional.of(new FusionMeasurableLayer(values, refOverlay, null, true, modality));
        }
      }
    }
    return Optional.empty();
  }

  /** DICOM Modality of the overlay series for stats labels; empty when unknown. */
  private static String overlayModalityLabel(MediaSeries<DicomImageElement> overlaySeries) {
    String modality = TagD.getTagValue(overlaySeries, Tag.Modality, String.class);
    return modality == null ? StringUtil.EMPTY_STRING : modality;
  }

  /**
   * Builds the affine mapping a displayed-image pixel (column, row) to a PET pixel (column, row),
   * via patient coordinates. Returns {@code null} when geometry is missing or the two planes are
   * not (near) parallel, in which case a single PET slice cannot represent the displayed plane.
   */
  private static AffineTransform baseToOverlayTransform(
      DicomImageElement baseImage, DicomImageElement overlayImage) {
    GeometryOfSlice base = baseImage.getSliceGeometry();
    GeometryOfSlice overlay = overlayImage.getSliceGeometry();
    if (base == null || overlay == null) {
      return null;
    }
    Vector3d baseNormal = base.getNormal();
    Vector3d overlayNormal = overlay.getNormal();
    if (baseNormal == null
        || overlayNormal == null
        || Math.abs(baseNormal.dot(overlayNormal)) < 0.999) {
      return null;
    }
    Point2D p0 = overlayPixelOf(base, overlay, 0, 0);
    Point2D px = overlayPixelOf(base, overlay, 1, 0);
    Point2D py = overlayPixelOf(base, overlay, 0, 1);
    if (p0 == null || px == null || py == null) {
      return null;
    }
    return new AffineTransform(
        px.getX() - p0.getX(),
        px.getY() - p0.getY(),
        py.getX() - p0.getX(),
        py.getY() - p0.getY(),
        p0.getX(),
        p0.getY());
  }

  /** Projects displayed-image pixel (col, row) through patient space into PET pixel coordinates. */
  private static Point2D overlayPixelOf(
      GeometryOfSlice base, GeometryOfSlice overlay, int col, int row) {
    if (base.getTLHC() == null
        || base.getRow() == null
        || base.getColumn() == null
        || base.getVoxelSpacing() == null) {
      return null;
    }
    return overlay.getImagePosition(base.getPosition(new Point2D.Double(col, row)));
  }

  /**
   * The window and ABGR LUT the overlay is currently colorized with, so the color bar can show the
   * exact scale being painted. Empty when fusion is off, has no overlay series or the view does not
   * fuse: the base image alone has no overlay, and the overlay alone reads in gray, its window
   * given by the view annotations.
   */
  @SuppressWarnings("unchecked")
  public Optional<FusionColorScale> getColorScale() {
    Boolean enabled = getParam(P_FUSION_ENABLED, Boolean.class);
    if (enabled == null
        || !enabled
        || getDisplay() != FusionDisplay.FUSED
        || !(params.get(P_FUSION_SERIES) instanceof MediaSeries<?> overlaySeries)) {
      return Optional.empty();
    }
    FusionWindow window = resolveWindow((MediaSeries<DicomImageElement>) overlaySeries);
    return Optional.ofNullable(window).map(w -> new FusionColorScale(w, buildAlphaLut()));
  }

  /** Clears the aligned overlay caches. Called when the LUT, series or volume changes. */
  public void clearCache() {
    sliceOverlayCache.clear();
    volumeOverlayCache.clear();
  }
}
