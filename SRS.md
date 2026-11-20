# Software Requirements Specification (SRS)
## Scan 3D — Release 1: Guided 30-Image Spherical Capture System

### 1. Scope (Release 1 — Capture & Store Only)
Per the **30-Image Spherical Panorama Stitching System (Part I & Part II)** specification, **Release 1** focuses exclusively on acquiring, validating, and storing all 30 spherical images and their orientation metadata cleanly on-device across Android 11+ (API 30+) and older supported phones (API 24+).

**Included in Release 1:**
- Home, Calibration (`START`), OpenGL ES 2.0 Virtual Sphere AR Tile screen, Review Grid, and Settings/Diagnostics screens.
- Guided & Free-order 30-position capture with `HOLD` state machine (`SEARCHING` -> `ALIGNED` -> `HOLDING` -> `CAPTURING`).
- CameraX `Preview` + `ImageCapture` (capped ~12MP, zoom locked `1.0x`) with `Camera2Interop` AE/AWB/AF lock.
- Real camera FOV extraction from `CameraCharacteristics` (`52° × 68°` fallback).
- Sensor fallback chain (`TYPE_GAME_ROTATION_VECTOR` -> `TYPE_ROTATION_VECTOR` -> `ACCELEROMETER + MAGNETIC_FIELD` -> Manual mode).
- On-device Quality Gate (Laplacian variance blur check, clipped/dark exposure check, corner texture check).
- Atomic file storage (`img_01.jpg`..`img_30.jpg`, `/thumbs/`, `session.json`), Room DB session persistence & resume, 4000-point sphere coverage check, 1-tap retake, and ZIP export via `FileProvider`.

**Deferred to Release 2 (Explicitly Excluded from Release 1):**
- Equirectangular stitching pipeline and "Stitch 360" button.
- Panorama viewer inside a sphere and GPano XMP metadata.
- Cloud processing and sharing of a stitched 360 image.

---

### 2. 30-Target Capture Geometry (Sections 3 & 15)
| ID(s) | Layer | Pitch | Yaw Positions | Count |
| --- | --- | --- | --- | --- |
| `1` | Zenith | `+90°` | `0°` | 1 |
| `2..9` | Upper | `+45°` | `0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°` | 8 |
| `10..21` | Centre | `0°` | `0°, 30°, 60°, ..., 330°` | 12 |
| `22..29` | Lower | `-45°` | `0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°` | 8 |
| `30` | Nadir | `-90°` | `0°` | 1 |
| **Total** | **5 Bands** | | | **30** |

Recommended guided capture sequence: **Centre (`10..21`) → Upper (`2..9`) → Zenith (`1`) → Lower (`22..29`) → Nadir (`30`)**.

---

### 3. OpenGL ES 2.0 AR Tile & HUD Workflow (Sections 16 & 23)
1. **Bottom Layer:** CameraX `PreviewView`.
2. **Middle Layer:** Transparent `GLSurfaceView` (OpenGL ES 2.0) drawing 3D spherical wireframe and the active green tile quad with circular hole shader (`if (length(p) < uHole) discard;`).
3. **Top Layer (Compose HUD):**
   - Fixed white reference frame at screen center.
   - Fixed white aiming circle (`START` during calibration, directional pointer arrow during search, `HOLD` with green clockwise progress ring over `0.8s` when aligned `< 3.5°` and steady `< 0.05 rad/s`).
   - 5-row 30-dot map at bottom (`White` = active, `Green` = completed, `Grey` = remaining, `Red` = needs retake).
   - Close (`X`) button to pause & resume later without losing progress.
