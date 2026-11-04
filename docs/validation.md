# Validation record and release gates

## Executed here

- The initial implementation was prepared in a separate local repository on `develop`. The owner subsequently selected the name Lensqivo and explicitly authorized publication to `SHUBHRAGHAVT-57/Lensqivo`. No FilterCam files were changed.
- `node scripts/test-core.mjs`: **421 assertions passed**, using Java 25.0.2 with Java 17 output target. Exercises repeated taps, startup stop, finalization ownership, stale callbacks, matrix intensity/neutrality, mono and finite-value behavior.
- Initial tests failed with missing CaptureGate/Look, then passed after implementation.
- With `javac -Xlint:all`, this local JDK fails writing the nested `CaptureGate$Phase.class`; removing that optional flag makes the same sources compile and all tests execute. Root cause in the local compiler/environment remains unresolved. The portable launcher uses `-Werror`; Gradle's core gate retains `-Xlint:all -Werror` for CI on JDK 17. Strict Gradle compilation has not run.
- PowerShell script execution is disabled locally. The test launcher uses Node; no execution policy was changed.
- Retrieved the official Gradle v9.8.2 wrapper JAR/scripts; JAR SHA-256: `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`. Distribution SHA-256 pinned from Gradle's own Dockerfile.
- Attempted `gradlew.bat :core:check :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --no-daemon`. Java's environment resolved the default Gradle home to `C:\.gradle`, which is unwritable. Retried with `--gradle-user-home .gradle-user-home`: wrapper download then failed with `java.net.SocketException: Permission denied: getsockopt`. No Android tasks executed.
- No local Android SDK, adb, Android Studio, Gradle distribution/cache or usable browser host was found. Direct SDK download also failed to connect.
- Checked a temporary cloud build route: Sandbox creation rejected the request because a Vercel project ID was required. No session/project was created; unrelated hosting resources were not provisioned.
- Fresh independent source review confirmed effect target combinations and key APIs, found original-only recovery matrix restoration and implicit rear-camera Play filtering issues; both corrected. Front-video mirroring also corrected against official documentation.
- Final review of the correction pass found no new concrete source blockers. XML parsing passed for all four manifest/resource XML files, and `git diff --check` passed after all new files were registered for intent-to-add. Intent-to-add records paths for review without staging file contents or making a commit. Core assertions were rerun successfully after the final source edits.

## Written but not executed

Publication CI initially failed because the runner did not expose `sdkmanager`. A pinned Android setup action provisions the command-line tools explicitly. The next run identified that API 37 uses the SDK package ID `platforms;android-37.0`, rather than `platforms;android-37`; the workflow now installs that exact package. These setup failures occurred before Android compilation, so they do not establish an application compile failure or success.

Instrumented tests cover capture action labeling, stop during startup, disabled finalization shutter, disabled look selection, and restoring the actual shader matrix after original-only recovery. They require an Android runtime and dependency resolution. The UI tests are not substitutes for capture tests.

CI is configured with pinned Actions, read-only repository permission, wrapper validation, JDK 17, SDK 37, core checks, Android debug build, lint and instrumented-test compilation. Check the live workflow for results after publication. Android syntax, linkage, lint, packaging, signing behavior and dependency compatibility remain unverified until that build passes.

## Hardware acceptance matrix

All rows below are **planned, not passed**. Use representative available devices; model names are coverage examples, not support claims.

| Coverage | Minimum scenarios |
|---|---|
| API 29/30 low-RAM device; Moto G-class | Startup, photo save, HD/SD fallback, rapid taps, denied audio, allocation pressure |
| Pixel 6/8-class; API 35–37 | Both cameras/modes, preview/output chart match, rotation and 180° orientation, autofocus, system picker |
| Galaxy S-series Snapdragon/Adreno | FHD encode, sound, cold/warm start, long clips, background/finalization, storage errors |
| Galaxy Exynos/Mali | Same tests plus effect initialization, pixel precision/banding, GPU memory and GL failure recovery |
| OnePlus + Xiaomi/MediaTek | Camera resource contention, permission revocation, focus crop coordinates, vendor quality fallback |
| Tablet/foldable or front-only emulator | Rear camera absent, resize/fold during preview/capture, navigation bars, split screen |
| TalkBack, 200% font/display scaling, RTL | All controls discoverable; shutter remains reachable; disabled and saving states distinguishable; dialogs scroll |

For every device, photograph a framing chart in portrait/landscape with front/rear cameras, all four looks at 0/50/100%, and comparison enabled. Inspect saved pixel dimensions, crop, orientation, mirror state, color, highlight/skin reproduction and EXIF. Compare actual saved media in a color-managed viewer; preview screenshots alone do not establish parity.

Record audible sync markers at start/middle/end of short and 30-minute clips; inspect MP4 tracks and decode the whole file. Interrupt via Home, Back, permission change, incoming call, screen lock, camera contention, storage exhaustion and process termination. A stop request must retain ownership until Finalize; never treat a recovered file as proof that every frame/audio segment survived.

Stress background/foreground and configuration changes during STARTING, RECORDING, FINALIZING and PHOTO. No new transaction may use resources owned by an incomplete transaction. Recordings may end on rotation/window changes; uninterrupted rotation is not a version-one guarantee. Process death/power loss cannot be made safe solely by callbacks; durable pending-media recovery remains a future gate.

## Performance hypotheses to measure

- The app replaces a 16-float matrix when settings change; it does not allocate that matrix per frame. Media3/CameraX allocations and render-pass counts must be measured rather than inferred from this code.
- One 12 MP RGBA8 image is about **45.8 MiB**. Two such GPU buffers would be about **91.6 MiB**, before camera/encoder/driver memory. Actual format, buffer counts and whether full-resolution effect capture is selected remain unknown. There is no app-owned full-resolution bitmap readback pipeline.
- Measure time to first preview, shutter-to-save latency, output size/quality, frame intervals, dropped frames, AV drift, PSS/GPU memory, thermal throttling and battery during 5/30-minute use. Use Perfetto/Android Studio profiling and GPU tools where supported.
- Compare Original-only, neutral effect, each look, rear/front, FHD/HD, cold/warm and low-memory states on Adreno and Mali. Never discard a high-quality path just to improve an average number.
- No FPS, battery, thermal or universal compatibility claim has been measured here.

## Before a release

- [ ] Android dependency resolution, strict core check, debug/release build, Android lint and test APK compilation pass.
- [ ] Instrumented tests pass; actual JPEG/MP4 files decode correctly on the device matrix.
- [ ] Preview/capture framing, mirroring, output dimensions and look parity pass chart tests.
- [ ] Denied/revoked permissions, camera contention, storage-full and lifecycle interruption fail clearly without false success.
- [ ] Long recording, audio interruption, partial file recovery and broken-output cleanup verified.
- [ ] TalkBack, large text, small windows and landscape visually inspected on real rendering.
- [ ] Decide licensing and check the working app name/package before publication; review third-party notices and current advisories.
- [ ] Configure a private signing key outside Git, build a signed app bundle, and verify current Play requirements/privacy declarations.
- [ ] Publish only after real build/device evidence; development source and passing core tests alone do not satisfy these gates.
