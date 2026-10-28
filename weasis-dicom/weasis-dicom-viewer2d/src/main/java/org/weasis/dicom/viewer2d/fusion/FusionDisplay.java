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

/**
 * What a view shows of a fused pair ({@link FusionOp#P_FUSION_DISPLAY}). Views fuse by default; the
 * MPR fusion layout also gives each image a row of its own, so the functional series can be read
 * without the anatomy under it and the anatomy without the colors over it.
 */
public enum FusionDisplay {
  /** The overlay blended over the base image. */
  FUSED,
  /** The base image alone, whatever the fusion settings. */
  BASE,
  /**
   * The overlay alone, in opaque inverted gray whatever the color LUT, as nuclear medicine reads
   * it: white background, dark uptake, through the shared window and transfer function.
   */
  OVERLAY
}
