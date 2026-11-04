<p align="center">
  <img src="assets/lensqivo-cover.jpg" alt="Lensqivo — Light. Color. Feeling. Original cover artwork with warm, cool and monochrome interpretations of sunlit architecture." width="1280" />
</p>

<h1 align="center">Lensqivo</h1>

<p align="center">
  <strong>An everyday Android camera with a considered sense of color.</strong><br />
  Native capture. Original live looks. A little more feeling in the frame.
</p>

<p align="center">
  <a href="https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/workflows/android.yml"><img src="https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/workflows/android.yml/badge.svg" alt="Android quality gates workflow status" /></a>
</p>

<p align="center">
  <a href="#the-camera">The camera</a> &nbsp;·&nbsp;
  <a href="#build-it">Build it</a> &nbsp;·&nbsp;
  <a href="#under-the-viewfinder">Architecture</a> &nbsp;·&nbsp;
  <a href="#development-status">Development status</a>
</p>

> **Development build · v0.2.0**
>
> This iteration introduces native still capture, natural beauty controls and a camera-first interface. [Get the latest successful development build from GitHub Actions](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/workflows/android.yml). Check its validation result before installing. Real-camera quality and cross-device performance require physical-device qualification; this is not a production release. The cover is AI-generated brand artwork, **not** an app screenshot or a photograph captured with Lensqivo.

## A name with a point of view

**Lensqivo** is pronounced **Lens-kee-vo**.

| Lens | Q | Vivo |
| :--- | :--- | :--- |
| Photography and camera optics | Quality | Vivid, lifelike imagery |

The idea is simple: a camera that puts the moment first, with a small collection of useful creative choices close at hand. Charcoal surfaces, warm paper tones and amber accents keep attention on the viewfinder.

## The camera

The following features are **implemented in source**. Device qualification is tracked separately in the [validation record](docs/validation.md).

| Capture | Color | Control |
| :--- | :--- | :--- |
| Native still JPEGs through CameraX | Eight original live looks | Tap-to-meter |
| MP4 video through CameraX Recorder | Adjustable look intensity | Capability-dependent zoom and exposure |
| Supported FHD → HD → SD selection | Original comparison with capture guard | Front / rear camera switching |
| Optional microphone audio | Skin-color-selective smoothness and soft glow | Composition grid, flash and 3/10-second timer |
| Private interrupted-photo recovery | Three reusable offline recipe slots | Saved-selfie mirroring and optional original copies |

The viewfinder leads. A stable shutter dock and compact toolbar keep the camera ready; Looks, Beauty and Adjust open only when needed. Settings group photo, video, composition, creative defaults and saving. A recording remains owned until CameraX reports finalization. Sound interruptions are surfaced instead of treating microphone permission as proof of recorded audio.

### Eight looks, room to make them yours

| Original | Sol | Dusk | Silver |
| :--- | :--- | :--- | :--- |
| True to the light | Warm light, soft color | Cool air, quiet color | Light and shadow |

| Cove | Ember | Petal | Fade |
| :--- | :--- | :--- | :--- |
| Clear blues, softened greens | Golden warmth, deeper color | Gentle rose, airy shadows | Muted color, lifted blacks |

These are original creative color transforms. They do not claim to reproduce a proprietary film stock. Grain, halation and advanced tone curves are research directions, not existing features. Shared rendering is designed to reduce preview/export differences; actual color, framing and output resolution still need chart-based device tests.

**Natural beauty, deliberately restrained.** Smoothness uses a feathered skin-color selector and edge-aware neighboring samples. Soft glow adds a gentle highlight treatment. Both default off. This is not face detection: similarly colored walls or clothing can also be affected. There is no automatic whitening, facial reshaping or claim to erase blemishes.

**A proper still-photo path.** Photos use CameraX's native still stream, separate from live-preview effects. Edited JPEGs retain captured pixel dimensions through a tiled GPU export on a worker. The same SDR shader supplies preview/video and photo color, with explicit sRGB conversion for JPEGs. Native originals are retained until publication succeeds. If memory or the GPU cannot complete an edit at full resolution, Lensqivo saves the original and explains the failure; it never silently shrinks the image. Optional original copies cost additional storage. This does not promise vendor-stock HDR/Night processing or exact preview/ISP equality.

## Local capture, fewer permissions

- The application manifest requests camera access and optional microphone access. It does not request Internet, location or broad media-library permissions.
- There are no accounts, advertisements or analytics integrations in the application source.
- Photos save to `DCIM/Lensqivo`; videos save to `Movies/Lensqivo` through Android MediaStore.
- Denying microphone access leaves silent video available. Recording stops when the application leaves the foreground.
- Your gallery and Android backup provider may apply their own settings to saved media. Lensqivo does not provide a cloud-upload service.

## Build it

### Prerequisites

| Tool | Project configuration |
| :--- | :--- |
| Android Studio | A version compatible with Android Gradle Plugin 9.2.1 |
| Android SDK | Platform 37.0 (API 37) and Build Tools 36.0.0 |
| JDK | 17 for the CI build |
| Gradle | 9.8.2 through the included wrapper |
| Minimum Android version | Android 10 / API 29 |

~~~sh
git clone https://github.com/SHUBHRAGHAVT-57/Lensqivo.git
cd Lensqivo
~~~

Open the project in Android Studio and configure the SDK location. The generated `local.properties` stays out of Git.

**Windows / PowerShell**

~~~powershell
.\gradlew.bat --gradle-user-home .gradle-user-home :core:check :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
~~~

**macOS / Linux**

~~~sh
bash gradlew :core:check :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
~~~

After a successful build, the development APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Its debug signature is for development; it is not a production release.

CI debug signing can change between runs. If Android rejects an update because its signature differs, save anything you need and recover any pending originals before uninstalling the earlier development build. Uninstalling removes app-private settings and pending files. Production updates require a controlled release signing key.

### Core tests without an Android SDK

With Java 17+ and Node.js 20+:

~~~sh
node scripts/test-core.mjs
~~~

The current suite passes **814 assertions** covering capture ownership, repeated taps, stop-before-start, finalization, stale callbacks and color-transform behavior. This assertion count is not a claim of 814 independent test scenarios.

On an isolated Android test device or emulator, run the instrumented UI/recovery tests with:

~~~sh
bash gradlew :app:connectedDebugAndroidTest
~~~

## Under the viewfinder

~~~text
app/                 Kotlin / Compose application
  CameraSession      CameraX resources, capabilities, capture and finalization
  CameraScreen       Viewfinder, creative controls, settings and semantics
  MainActivity       Permissions, ViewModel ownership, picker and media opening
  PhotoStore         Pending-media transactions and native-original recovery
  CreativeEffect     Shared SDR shader and natural beauty effect
  PhotoRenderer      Tiled native-resolution still export
core/                Java capture-state gate and original color math
scripts/             Dependency-free core test launcher
assets/              Original repository artwork and provenance
docs/                Product decisions, research and validation requirements
~~~

CameraX owns the camera and recording pipeline; the app does not implement its own MediaCodec/MediaMuxer recorder. Media3 supplies live GPU effects. Native stills are region-decoded and processed with that same shader, with one full output bitmap plus bounded overlapping tiles. Export checks available heap and texture limits. A 12 MP output bitmap alone is about 45.8 MiB; capture and GPU resources require additional memory.

The CameraX Media3 adapter is currently **`1.0.0-alpha04`**. Its integration is isolated in `CameraSession`; compatibility, memory use and image quality require real-device validation. See the [research and technical decisions](docs/research.md) for sources and trade-offs.

## Development status

| Gate | Current evidence |
| :--- | :--- |
| Core behavior | 814 assertions executed successfully locally; current CI result linked below |
| Manifest and resource XML | Parsed successfully |
| Source formatting checks | `git diff --check` passed before publication |
| Android build and lint | [Current workflow results](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/workflows/android.yml); previous v0.1 source passed |
| Instrumented tests | CI configured to execute native Compose, GPU export, preferences and MediaStore regression tests on an isolated emulator |
| Real photo/video, GPU parity and accessibility | Device qualification pending |

The [Android quality workflow](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/workflows/android.yml) validates the wrapper, runs the core gate, builds the debug app, runs strict Android lint, compiles the instrumented tests and executes them on an isolated emulator. Synthetic scenes test UI and export correctness; these do not validate a physical camera or certify thermal performance. Check the actual workflow result before relying on an artifact.

### What comes next

1. Pass the complete Android build and qualify real JPEG/MP4 capture across representative devices.
2. Verify preview/output framing, color, mirroring and saved resolution with test charts.
3. Stress recording lifecycle, sound interruptions, low storage and process death.
4. Refine film-style tone and grain only after the existing capture path is proven.

RAW video, Log/HDR, LUT import, manual sensor controls, cloud sync and subscriptions are outside this version. The [validation checklist](docs/validation.md) defines the release gates and device matrix.

## Contributing and ownership

Bug reports with reproduction steps and expected/actual behavior are useful. Hardware details are optional; share them privately if preferred. Do not include personal photos, private media links or signing material in public reports.

Lensqivo is an independent implementation. No FilterCam source, presets, shaders, branding or artwork were copied. Gradle wrapper files and AndroidX dependencies retain their upstream licenses. No license grant for Lensqivo's original source has been selected yet; public visibility alone does not grant permission to reuse it. Cover provenance is documented [here](assets/README.md).

<p align="center"><strong>Lensqivo</strong> · Light. Color. Feeling.</p>
