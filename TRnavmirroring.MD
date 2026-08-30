# Thinkerride Navigation & TFT Screen Mirroring Architecture

## 1. Executive Summary

Thinkerride does not use traditional phone screen capture (which merely clones the smartphone's screen and causes black bars / aspect ratio mismatch). Instead, it creates an independent Android **`VirtualDisplay`** paired with an `android.app.Presentation` dialog rendered directly into a hardware-accelerated **`MediaCodec` H.264 video encoder**, streaming to the motorcycle's TFT instrument cluster over Wi-Fi TCP sockets.

This allows Thinkerride to:
1. Render a dedicated UI completely independent of what is displayed on the phone's primary screen (the phone screen can even be turned off or switched to another app).
2. Render at the exact native resolution and aspect ratio of the motorcycle cluster (eliminating letterboxing and black bars).
3. Achieve low-latency, hardware-accelerated H.264 video streaming over local Wi-Fi.

---

## 2. Architectural Pipeline

```
[ProjectionView (Map & Turn-by-Turn Navigation UI)]
                         │
                         ▼
        [Presentation (Android Secondary Window)]
                         │
                         ▼
    [VirtualDisplay (Exact TFT Cluster Resolution)]
                         │
                         ▼
     [MediaCodec Input Surface (H.264 Encoder)]
                         │  (GPU-direct hardware encoding)
                         ▼
[TCP Socket Streamer (Port: 15456 / 17818 - H.264 NAL)]
                         │  (Wi-Fi 5GHz / 2.4GHz)
                         ▼
  [Motorcycle TFT Head Unit (H.264 Hardware Decoder)]
```

---

## 3. Detailed Component Breakdown

### A. Independent Display Creation (`VirtualDisplay` & `Presentation`)

1. **Virtual Display Creation:**
   In `ProjectionEncoder.java` and `RoadNaviHelper.java`, Android's `DisplayManager` is queried to create a virtual display:
   ```java
   this.m = displayManager.createVirtualDisplay(
       "projection",
       targetWidth,
       targetHeight,
       dpi,            // Default: 300 - 320 DPI
       inputSurface,   // Surface created from MediaCodec.createInputSurface()
       0
   );
   ```

2. **Secondary Window Management (`ProjectionScreen`):**
   `ProjectionScreen` extends `android.app.Presentation`, a specialized Android dialog that binds to an external/virtual `Display`:
   ```java
   public class ProjectionScreen extends Presentation implements DialogInterface.OnDismissListener, DialogInterface.OnShowListener {
       public ProjectionScreen(Context context, Display display, BaseProjectionView baseProjectionView) {
           super(context, display);
           ...
       }

       @Override
       public void onCreate(Bundle bundle) {
           super.onCreate(bundle);
           addContentView(this.a.getView(), new WindowManager.LayoutParams(-1, -1)); // MATCH_PARENT x MATCH_PARENT
       }
   }
   ```
   * Because `Presentation` targets the virtual display, its lifecycle and views are decoupled from the phone's activity stack.
   * `WindowManager.LayoutParams(-1, -1)` ensures that the view takes 100% of the canvas without any margin or system decorations.

---

### B. Aspect Ratio & Zero Black-Bar Adaptation

Motorcycles use a variety of screen shapes (horizontal widescreen, vertical tall, square, circular, etc.). Thinkerride adapts the canvas size and layout dynamically:

1. **Screen Shape Detection (`ProjectionUtil.java`):**
   When the bike connects via BLE/Wi-Fi, its device profile defines the hardware `ScreenShape`:
   * **Horizontal Widescreen:** `1280 x 640` / `1280 x 720`
   * **Large Vertical:** `640 x 1284`
   * **Standard Vertical:** `480 x 800`
   * **Circular (Round Cluster):** `800 x 800`
   * **Ping-Pong / Oval:** `800 x 640`
   * **TaiLin Large Horizontal:** `1024 x 600`
   * **TaiLin 480P Horizontal:** `800 x 480`

2. **Dynamic Layout Inflation (`ProjectionView.java`):**
   Based on the detected screen shape, the app loads a layout specifically tailored for that form factor:
   ```java
   if (ProjectionUtil.isVerticalScreenBig()) {
       this.c = this.w.inflate(R.layout.layout_navigation_projection_vertical_800x, null);
   } else if (ProjectionUtil.isCircle()) {
       this.c = this.w.inflate(R.layout.layout_navigation_projection_circle, null);
   } else if (ProjectionUtil.isVerticalScreen()) {
       this.c = this.w.inflate(R.layout.layout_navigation_projection_vertical, null);
   } else {
       this.c = this.w.inflate(R.layout.layout_navigation_projection, null);
   }
   ```

3. **Map Viewport and Padding Adaptation:**
   For navigation engines (Mapbox / Amap), the camera centering and edge paddings are adjusted to keep navigation icons, maneuver arrows, and the route centered relative to the motorcycle cluster's dimensions:
   ```java
   MapHelper.getInstance().getOptionsHub().setMapViewOptions(
       4,
       new SqCustomViewOptions.Builder()
           .center(currentLocation)
           .followingPadding(new SqMapPadding(200.0d, 250.0d, 400.0d, 250.0d))
           .build()
   );
   ```

---

### C. Hardware Encoding & Low-Latency Streaming (`MediaCodec` & TCP)

1. **Hardware H.264 Encoder Configuration:**
   In `ProjectionEncoder.java`, a hardware H.264 encoder is initialized:
   ```java
   MediaFormat format = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, width, height);
   format.setInteger("bitrate", width * height);
   format.setInteger("frame-rate", 30);
   format.setInteger("color-format", MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); // 0x7F000789
   format.setInteger("i-frame-interval", 1);
   format.setLong("repeat-previous-frame-after", 100000L); // 100ms frame repeating
   format.setInteger("bitrate-mode", MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

   if (CodecSupportChecker.isAVCHighProfileSupported()) {
       format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
       format.setInteger("level", MediaCodecInfo.CodecProfileLevel.AVCLevel4);
   }
   ```

2. **Direct Surface Pipeline (Zero-Copy):**
   * Calling `this.k.createInputSurface()` binds the encoder directly to the Android graphics compositor (SurfaceFlinger).
   * Rendering `Presentation` onto `VirtualDisplay` immediately draws into this input `Surface`.

3. **Transmission over Wi-Fi Socket:**
   * Encoded NAL units (SPS, PPS, Keyframes, Interframes) are dequeued via `MediaCodec.dequeueOutputBuffer(...)` and transmitted over a TCP socket connection (ports `15456` or `17818`).
   * The motorcycle's onboard embedded receiver decodes the raw H.264 stream and paints directly to the physical TFT framebuffer.

---

## 4. Comparison: Thinkerride vs. Traditional Screen Mirroring

| Feature | Thinkerride Architecture | Standard Screen Mirroring (MediaProjection / Miracast) |
| :--- | :--- | :--- |
| **Render Target** | Independent `VirtualDisplay` + `Presentation` | Primary phone display mirror |
| **Phone Screen State** | Screen can be off, locked, or running other apps | Screen must remain active on the navigation app |
| **Aspect Ratio / Resolution** | Matches the exact native TFT resolution | Matches the phone's resolution (creates black bars) |
| **UI Customization** | Dedicated cluster UI (large icons, high contrast) | Phone layout scaled down (hard to read while riding) |
| **Encoding Overhead** | Direct GPU surface rendering (`createInputSurface`) | Screen capture buffer copy |
