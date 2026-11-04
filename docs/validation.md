# Validation record and release gates

## Version 0.2 iteration

- Local core suite: **814 assertions passed** after adding Cove, Ember, Petal and Fade; the added palette test first failed against the original four-look implementation.
- Native still capture now avoids targeting ImageCapture with a shared surface effect. Photo export uses a shared SDR shader with overlapping region-decoded tiles, retains captured dimensions/EXIF orientation, and preserves the native original on an edit failure.
- Added regression coverage for original JPEG byte preservation, tiled grayscale/color processing, non-square orientation, EXIF retention/GPS exclusion on edited copies, skin-texture/edge behavior, tile seams, invalid input, main-thread rejection, MediaStore pending publication, failed-edit fallback, preference validation and local recipes.
- CI now executes instrumented tests on an isolated API 35 x86_64 software-GPU emulator, with cameras disabled. This tests synthetic UI/export fixtures, not real camera behavior. Check the current workflow result; test execution is not established merely by configuration.
- The exact shared GLSL was exercised separately in Chromium/SwiftShader: zero byte error for neutral live/JPEG transforms and for full-frame versus overlapping tiled output in the synthetic fixture. Removing overlap changed pixels, demonstrating fixture sensitivity. This is software-renderer evidence, not Android camera, hardware GPU, FPS or thermal qualification.
- No local SDK/adb is available. An attempted Android test compilation failed during Gradle distribution download with `SocketException: Permission denied: getsockopt`, before Kotlin compilation.
- Private device information and user screenshots are excluded from source, artwork and public documentation. The bundled sample still life is separately generated artwork.

## Executed here

- The initial implementation was prepared in a separate local repository on `develop`. The owner subsequently selected the name Lensqivo and explicitly authorized publication to `SHUBHRAGHAVT-57/Lensqivo`. No FilterCam files were changed.
- `node scripts/test-core.mjs`: **421 assertions passed**, using Java 25.0.2 with Java 17 output target. Exercises repeated taps, startup stop, finalization ownership, stale callbacks, matrix intensity/neutrality, mono and finite-value behavior.
- Initial tests failed with missing CaptureGate/Look, then passed after implementation.
- With `javac -Xlint:all`, this local JDK fails writing the nested `CaptureGate$Phase.class`; removing that optional flag makes the same sources compile and all tests execute. Root cause in the local compiler/environment remains unresolved. The portable launcher uses `-Werror`; Gradle's core gate retains `-Xlint:all -Werror` and now passes on JDK 17 in CI, including all 421 assertions.
- PowerShell script execution is disabled locally. The test launcher uses Node; no execution policy was changed.
- Retrieved the official Gradle v9.8.2 wrapper JAR/scripts; JAR SHA-256: `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`. Distribution SHA-256 pinned from Gradle's own Dockerfile.
- Attempted `gradlew.bat :core:check :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --no-daemon`. Java's environment resolved the default Gradle home to `C:\.gradle`, which is unwritable. Retried with `--gradle-user-home .gradle-user-home`: wrapper download then failed with `java.net.SocketException: Permission denied: getsockopt`. No Android tasks executed.
- No local Android SDK, adb, Android Studio, Gradle distribution/cache or usable browser host was found. Direct SDK download also failed to connect.
- Checked a temporary cloud build route: Sandbox creation rejected the request because a Vercel project ID was required. No session/project was created; unrelated hosting resources were not provisioned.
- Fresh independent source review confirmed effect target combinations and key APIs, found original-only recovery matrix restoration and implicit rear-camera Play filtering issues; both corrected. Front-video mirroring also corrected against official documentation.
- Final review of the correction pass found no new concrete source blockers. XML parsing passed for all four manifest/resource XML files, and `git diff --check` passed after all new files were registered for intent-to-add. Intent-to-add records paths for review without staging file contents or making a commit. Core assertions were rerun successfully after the final source edits.

## CI verification and tests still outstanding

On 4 November 2026, [Actions run 41839194868](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/runs/41839194868) completed successfully at source commit `ed85c25193738e503dc4b42286e0279e25473764`. The required `:core:check`, `:app:assembleDebug`, `:app:lintDebug` and `:app:assembleDebugAndroidTest` tasks passed with warnings treated as errors in Android lint. The development APK and lint report are available in its `lensqivo-development-build` artifact. Instrumented tests were compiled, not executed.

Publication CI initially failed because the runner did not expose `sdkmanager`. A pinned Android setup action provisions the command-line tools explicitly. The next run identified that API 37 uses the SDK package ID `platforms;android-37.0`, rather than `platforms;android-37`; the workflow now installs that exact package. These setup failures occurred before Android compilation, so they do not establish an application compile failure or success.

Instrumented tests cover capture action labeling, stop during startup, disabled finalization shutter, disabled look selection, and restoring the actual shader matrix after original-only recovery. They require an Android runtime and dependency resolution. The UI tests are not substitutes for capture tests.

CI is configured with pinned Actions, read-only repository permission, wrapper validation, JDK 17, SDK 37.0, core checks, Android debug build, lint and instrumented-test compilation. CI exposed and verified corrections for nested Compose layout receiver access, an intensity-setter JVM signature collision, standalone core-test task wiring, modern backup exclusion rules, and the existing Kotlin helper APIs. Debug compilation, dependency resolution, packaging and lint are verified by the linked run. Minified release builds, production signing and real-device behavior remain unverified.

## Hardware acceptance matrix

All rows below are **planned, not passed**. Use representative available devices; model names are coverage examples, not support claims.

| Coverage | Minimum scenarios |
|---|---|
| API 29/30 low-RAM device | Startup, photo save, HD/SD fallback, rapid taps, denied audio, allocation pressure |
| Pixel 6/8-class; API 35–37 | Both cameras/modes, preview/output chart match, rotation and 180° orientation, autofocus, system picker |
| Galaxy S-series Snapdragon/Adreno | FHD encode, sound, cold/warm start, long clips, background/finalization, storage errors |
| Galaxy Exynos/Mali | Same tests plus effect initialization, pixel precision/banding, GPU memory and GL failure recovery |
| OnePlus + Xiaomi/MediaTek | Camera resource contention, permission revocation, focus crop coordinates, vendor quality fallback |
| Tablet/foldable or front-only emulator | Rear camera absent, resize/fold during preview/capture, navigation bars, split screen |
| TalkBack, 200% font/display scaling, RTL | All controls discoverable; shutter remains reachable; disabled and saving states distinguishable; dialogs scroll |

For every device, photograph a framing chart in portrait/landscape with front/rear cameras, all eight looks at 0/50/100%, beauty off/low/high and diverse skin tones under daylight/tungsten/low light. Comparison must prevent an unintended unprocessed capture. Inspect saved dimensions, crop, orientation, mirror state, color, highlight/skin reproduction and EXIF. Inspect warm wood/clothing for false-positive skin classification. Compare saved media in a color-managed viewer; preview screenshots alone do not establish parity.

Record audible sync markers at start/middle/end of short and 30-minute clips; inspect MP4 tracks and decode the whole file. Interrupt via Home, Back, permission change, incoming call, screen lock, camera contention, storage exhaustion and process termination. A stop request must retain ownership until Finalize; never treat a recovered file as proof that every frame/audio segment survived.

Stress background/foreground and configuration changes during countdown, STARTING, RECORDING, FINALIZING and PHOTO. No new transaction may use resources owned by an incomplete transaction. Recordings may end on rotation/window changes; uninterrupted rotation is not guaranteed. Kill the process at each native-photo/export/publication boundary; recovery must retain complete originals, avoid duplicate committed originals, and delete only this app's interrupted pending rows associated with retained captures. Callback tests alone do not establish power-loss durability.

## Performance hypotheses to measure

- The app replaces a 16-float matrix when settings change; it does not allocate that matrix per frame. Media3/CameraX allocations and render-pass counts must be measured rather than inferred from this code.
- A 12 MP output bitmap is about **45.8 MiB**. Export preflight requires that bitmap plus 48 MiB of heap headroom; GPU tiles and camera/driver buffers add further native/graphics memory. Tiling bounds individual texture sizes, but Android's JPEG compressor still needs a full output bitmap. Measure actual PSS/native/GPU peaks; insufficient memory must preserve/save the original, with an explicit edit failure and no downsampling.
- Measure time to first preview, shutter-to-save latency, output size/quality, frame intervals, dropped frames, AV drift, PSS/GPU memory, thermal throttling and battery during 5/30-minute use. Use Perfetto/Android Studio profiling and GPU tools where supported.
- Compare Original-only, neutral effect, each look, rear/front, FHD/HD, cold/warm and low-memory states on Adreno and Mali. Never discard a high-quality path just to improve an average number.
- No FPS, battery, thermal or universal compatibility claim has been measured here.

## Before a release

- [x] Android dependency resolution, strict core check, debug build, Android lint and test APK compilation pass in CI.
- [ ] Minified release build and production signing pass.
- [ ] Instrumented tests pass; actual JPEG/MP4 files decode correctly on the device matrix.
- [ ] Preview/capture framing, mirroring, output dimensions and look parity pass chart tests.
- [ ] Denied/revoked permissions, camera contention, storage-full and lifecycle interruption fail clearly without false success.
- [ ] Long recording, audio interruption, partial file recovery and broken-output cleanup verified.
- [ ] TalkBack, large text, small windows and landscape visually inspected on real rendering.
- [ ] Decide licensing and check the working app name/package before publication; review third-party notices and current advisories.
- [ ] Configure a private signing key outside Git, build a signed app bundle, and verify current Play requirements/privacy declarations.
- [ ] Publish only after real build/device evidence; development source and passing core tests alone do not satisfy these gates.
