# ScanForge 3D & 360 Photo Cam (Local-First)

**ScanForge 3D** is a local-first, offline Android 16 application combining the functionality of:
1. **360 Photo Cam (`com.dospace.photo360`)**: Full 360° Photo Sphere and spherical panorama capture with on-device stitching and interactive gyroscope/VR viewing.
2. **Local Multi-View 3D Object Scanner**: Photogrammetry and 3D Gaussian Splatting (3DGS) multi-view scanner with spherical layered guidance and quality gating.

---

## 1. 360 Photo Cam (DoSpace Style) Features

- **Floating AR Bubble Target Alignment:** As you turn around, floating target dots appear in AR world-space (covering horizontal 360° ring, upper angle ring, lower angle ring, zenith, and nadir).
- **Auto-Snap Gating & Artificial Horizon:** Center crosshair locks onto dots with haptic vibration, leveling status, and automatic capture.
- **On-Device Local Stitching:** Seamlessly compiles captured photographic viewpoints into a complete Equirectangular 360x180 panorama bitmap without requiring any network or cloud servers.
- **Interactive 360° Panorama Viewer:**
  - **Gyroscope Look:** Physically move and tilt the phone to look around in full 360 degrees.
  - **360° Orbit:** Touch swipe and pinch zoom.
  - **VR Split Screen:** Stereoscopic dual-eye mode for Google Cardboard / VR headsets.
  - **Equirectangular Flat Mode:** View unwrapped spherical panorama.
  - **Export:** Save high-res 360° JPEG to local storage.

---

## 2. 3D Object Scanner Features (SRS / PRD)

- **Spherical Layered Guidance:** Top surface, upper ring (+40°), middle ring (0°), lower ring (-30°), and bottom surface (-70°).
- **Real-Time Quality Gating:** Target closeness, device stability, blur/sharpness threshold, exposure range, and redundancy filtering (Green / Yellow / Red semantics).
- **Interactive 3D Viewer:** Multi-touch orbit, pan, and zoom with 3D Gaussian Splats, point cloud, wireframe, and shaded mesh modes.
- **Standard 3D Export:** Export to standard `.ply` and `.obj` formats.
- **Document & PRD Analysis Dashboard:** Complete accessible requirements audit (FR-01 to FR-15), WCAG 2.1 AA compliant with 48dp+ touch targets, TalkBack descriptions, and haptic feedback.

---

## 3. Running & Testing

- **Local JVM Tests:** `gradle :app:testDebugUnitTest` (All tests passing).
- **Simulation Mode:** On-screen virtual heading and orbit controls allow complete end-to-end testing of 360° capture, stitching, and viewing on emulators without physical movement.
