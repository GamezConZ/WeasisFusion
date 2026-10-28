# MPR Architecture Guide

This document describes the Multi-Planar Reconstruction (MPR) system in Weasis: its main
classes, the conventions they share, and how a user interaction becomes a rendered slice.

Code lives in `org.weasis.dicom.viewer2d.mpr` (module `weasis-dicom-viewer2d`).
Related documents:

- [mpr-coordinate-systems.md](mpr-coordinate-systems.md) — definitions of the coordinate spaces
- [mpr-transformations.md](mpr-transformations.md) — the slice ↔ volume transforms

---

## 1. Overview

The MPR system reconstructs three orthogonal slice views (Axial, Coronal, Sagittal)
from a 3D volume and lets the user:

- **Move** the crosshair to navigate through the volume.
- **Rotate** (tilt) the crosshair to view oblique slices.
- **Scroll** along the slice normal to step through slices.
- **Adjust MIP thickness** for Maximum Intensity Projection.

```
┌────────────────────────────────────────────────────────┐
│                     MprContainer                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  MprView     │  │  MprView     │  │  MprView     │  │
│  │  (AXIAL)     │  │  (CORONAL)   │  │  (SAGITTAL)  │  │
│  │              │  │              │  │              │  │
│  │  MprAxis ◄───┼──┼── MprAxis ◄──┼──┼── MprAxis    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                 │          │
│         └────────┬────────┴────────┬────────┘          │
│                  │                 │                   │
│           MprController       AxesControl              │
│            (mouse input)    (center, rotation)         │
│                  │                 │                   │
│                  └────────┬────────┘                   │
│                           │                            │
│                      Volume<?,?>                       │
│                     (voxel data)                       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Key Classes

| Class | Responsibility |
|---|---|
| `MprContainer` | Top-level viewer; creates the views, gives them their role in the layout and links the views of a plane |
| `MprView` | Swing panel (extends `View2d`, implements `SliceCanvas`) showing one `Plane`; converts mouse ↔ model coordinates and draws the crosshair |
| `MprController` | Shared by all the views; handles all crosshair mouse interactions (move, move one line, rotate, MIP thickness) and recentering |
| `AxesControl` | Global crosshair state: 3D center, global rotation, per-plane rotation offsets; projects the center onto a view |
| `MprAxis` | Links a `Plane` to its primary view, its linked views, `VolImageIO` and image; builds the slice → volume transform; slice index and MIP thickness |
| `MprViewRole` | Plane and fusion display of a view from its rank among the MPR cells of the layout |
| `AxisDirection` | Anatomical axis directions, colors and orientation arrows of a plane (overlay only) |
| `Volume<?,?>` | 3D voxel data: size, spacing, slice size, sampling |
| `VolImageIO` | Image I/O bridge that renders a slice from the volume |
| `SliceCanvas` | Interface for canvases that display slices |
| `ArcBallController` | Alternative 3D rotation input mode |

`Plane` is an enum in `MprView`: `AXIAL` (normal along Z), `CORONAL` (normal along Y),
`SAGITTAL` (normal along X).

---

## 3. Conventions

### 3.1 Patient coordinate system: DICOM LPS

The 3D volume space is **DICOM LPS**; [JOML](https://github.com/JOML-CI/JOML) is only the
math library:

| Axis | Anatomical direction | LPS |
|---|---|---|
| +X | Patient's Left | **L** |
| +Y | Patient's Posterior | **P** |
| +Z | Patient's Superior (head) | **S** |

The `AxisDirection` vectors are **anatomical orientation arrows** for the on-screen overlay;
they are **not** the volume coordinate axes.

### 3.2 Slice index vs. DICOM instance order

`MprAxis.getSliceIndex()` / `setSliceIndex()` translate between the depth of the crosshair
along the plane normal and the slice index shown to the user. The depth increases toward +Z
(Superior) for AXIAL, while axial series are conventionally numbered from Superior to
Inferior, so `AxisDirection.isInvertedDirection()` is `true` for AXIAL only.

### 3.3 Coordinate spaces

Almost all crosshair logic works in the **isotropic slice space**, a cube of
`sliceSize × sliceSize × sliceSize` where `sliceSize` is the volume diagonal
(`Volume.getSliceSize()`). Volume sampling happens in **VR-space**, then in voxel indices.
See [mpr-coordinate-systems.md](mpr-coordinate-systems.md).

---

## 4. Rotation System

The rotation of a view has three layers, all held by `AxesControl`:

1. **Base plane rotation** (`getRotationForSlice()`) — fixed, orients each plane relative
   to the volume axes:

   | Plane | Rotation | Effect |
   |---|---|---|
   | AXIAL | Identity | Canvas XY = Volume XY |
   | CORONAL | Rx(-90°) | Canvas XY maps to Volume XZ |
   | SAGITTAL | Ry(90°) · Rz(90°) | Canvas XY maps to Volume YZ |

   The coronal plane also applies `S(1, -1, 1)` (Y-flip) for correct anatomical orientation.
2. **Global rotation** — accumulates every tilt of the crosshair (`rotateAroundAxis()`), and
   affects all views.
3. **Per-plane rotation offset** — cancels the global rotation for the view where the tilt was
   made, so that view is unchanged and only the two other views are affected.

`getViewRotation(plane)` combines layers 2 and 3. See
[mpr-transformations.md](mpr-transformations.md) for the full forward and inverse chains.

---

## 5. Volume Center vs. Canvas Projection

There are two ways to read the crosshair position, and code must use the right one:

| Method | Returns | Use for |
|---|---|---|
| `MprController.getCrossHairPosition()` | the 3D center from `AxesControl.getCenter()` | rendering and volume operations (`MprAxis`, `VolImageIO`) |
| `MprController.getCrossHairPosition(MprAxis)` | the center projected onto that view's canvas (`AxesControl.getCenterForCanvas()`) | display, hit-testing, mouse interaction |

The rendering code interprets the position as a 3D volume position, so it must never receive
a canvas projection. When adding code that needs the crosshair, ask: *"Do I need the 3D
position or the 2D projection on a specific view?"*

---

## 6. Data Flow

### 6.1 Interaction

```
Mouse drag on view V (MprController)
    │
    ├─ move center / move one line / rotate / MIP thickness
    │     └─ update AxesControl (center or rotation)
    │
    ├─ re-render the two other views (MprAxis.updateImage())
    └─ recenter the other views so the crosshair stays visible
```

Scrolling changes the depth of the center along the view normal
(`AxesControl.setCenterAlongAxis()`), then recenters.

### 6.2 Rendering

```
MprAxis.updateImage()
    └─ VolImageIO.getImageFragment()
          ├─ volume center = MprController.getCrossHairPosition()
          ├─ MprAxis.getRealVolumeTransformation()   slice pixel → VR-space
          └─ Volume.getVolumeSlice()                 VR-space → voxel, interpolation
                (MIP: several slices combined)
```

---

## 7. Cross-Axis Pairing

Each view displays crosshair lines for the other two planes:

| View | Horizontal line | Vertical line |
|---|---|---|
| AXIAL | CORONAL (pair.first) | SAGITTAL (pair.second) |
| CORONAL | AXIAL (pair.first) | SAGITTAL (pair.second) |
| SAGITTAL | AXIAL (pair.first) | CORONAL (pair.second) |

Defined by `MprController.getCrossAxis()`. The `selectedAxis` tracks which cross-plane's
line the user is interacting with.

---

## 8. Graphics across planes

A graphic drawn on an MPR view is anchored in patient space like any graphic drawn on a located
image (see `weasis-core/docs/Measurement-Tools.md`): the reslice element carries the orientation
and position of its plane, so the anchor is set when the drawing is released.

`MprGraphicStore`, owned by `MprController`, keeps every anchored graphic of the volume. When a
view changes plane, `MprAxis` rebuilds the view's model from the store: a graphic whose points lie
in the new plane (within half the slice thickness) is placed back, editable, at the projection of
its anchor; a graphic that crosses the plane is shown as marks where its segments intersect it,
on the non-serializable `FOOTPRINT` layer. Editing or removing a graphic in one view updates the
marks of the other two. A volume without patient geometry (stacked, not rectified) keeps the
previous behaviour: the model of each exact plane is kept aside and restored when that plane
comes back.

## 9. Fusion layouts (CT / NM / fused)

`MprContainer.VIEWS_3x3_FUSION` shows each plane in a column of three rows: the base image alone,
the functional overlay alone, then both fused. `MprContainer.VIEWS_2x3_FUSION` keeps the base row
and the fused row. `MprViewRole` gives each view its plane and row (`FusionDisplay` — see
[fusion-architecture.md](fusion-architecture.md)); the other layouts show each plane once, fused.

- **One slice per plane.** The first view of a plane is the primary view its `MprAxis` renders
  through; the other views of the column are linked views (`MprAxis.setLinkedViews`). They display
  the same image element, refreshed by `MprAxis.updateImage` after the primary, so the plane is
  resliced once. Each view pins that element in the image cache, and the reference-counted pin is
  released when the view is disposed (see `weasis-core/docs/Memory-Management.md`). They also share
  its graphic model, so a measurement drawn in one row shows in the others.
- **Synchronization.** Crosshair, scroll, rotation and MIP act on the shared `AxesControl` and
  repaint every view (`MprController.repaintAllViews`). The views of a column share their zoom
  and pan through a view-model listener of the axis (`MprView.followViewport`, which also copies
  the zoom action so a resize computes the same zoom again); a linked view takes the viewport of
  the primary view as soon as it is linked. A pan made by the reader is carried to the other
  columns by its displacement in the volume (`MprController.panOtherPlanes`). Zoom and the window
  of the base image follow the MPR synchronization, which covers every row; the overlay window and
  fusion settings are shared through `FusionController`.
- **Layout switches.** Before switching to or from these layouts, the container clears every view
  but the primary views, so they are disposed of with their pin instead of being moved into a cell
  of another plane, row or kind; it then reassigns the roles, relinks the planes, gives the new
  rows the fusion shown before the switch and puts every view back on the best fit, computed again
  once the cells have their size.

## 10. Rules for Contributors

- **Rotation consistency.** The forward transform (display → texture,
  `MprView.getDisplayPointToTexturePointMatrix()`) and the inverse (texture → display,
  `AxesControl.getCenterForCanvas()`) must use matching rotations. If you change one, update
  the other; otherwise the crosshair drifts on tilted planes.
- **Adding a rotation mode.** Update `rotateAroundAxis()`, make sure `getViewRotation()`
  still cancels the rotation for the originating view, check that the inverse projection
  still inverts the full rotation chain, and test with each of the three planes as the
  originating view.
- **The `adjusting` flag.** While `MprController.isAdjusting()` is true (during a drag), MIP
  slabs are not rendered; the full MIP is rendered when the interaction ends.
