# Lensqivo engineering decisions

Lensqivo is an Android camera application maintained by Shubh Raghav. The application combines a guarded capture lifecycle, CameraX/Media3 integrations, shared color transforms, and a Compose interface. Upstream camera, codec, UI, and GPU infrastructure retains its own authorship and licensing.

## Capture ownership survives a stop request

The Java `CaptureGate` separates starting, recording, finalizing, and photo work. The recorder remains owned after a stop request until CameraX reports finalization. That prevents repeated taps or stale callbacks from opening a second transaction while output is still being saved. The dependency-free Java suite covers these transitions and color math. Its 814 assertions ran locally on Java 25 with a Java 17 compilation target.

## Preserve originals before publishing edited media

Native still capture uses a separate stream from the live surface effect. Edited JPEGs use the shared SDR shader and bounded overlapping GPU tiles while retaining the full output dimensions. Native originals remain available until publication succeeds. If a full-resolution edit fails, the app can save the original and report the failed edit. A 12 MP output bitmap alone costs about 45.8 MiB. Peak memory, color and framing parity, codec behavior, and thermals require hardware measurements.

## Keep evidence reachable and build both variants

An expiring artifact URL previously served as the primary evaluation link. [Evaluation](EVALUATION.md) now provides an account-free interface/source walkthrough, a stable workflow page, a dated successful-run fallback, and local build instructions. It clearly states when sign-in or an Android build environment is required.

The workflow uses pinned Actions, read-only permissions, wrapper validation, strict core compilation, warnings-as-errors Android lint, and software emulators on API 29 and 35. The build gate runs clean debug and minified release assembly, runs lint for both variants, and records the checked-out commit and APK hashes. Gradle explicitly uses Build Tools 36.0.0, matching the CI SDK package. The release assembly produces an unsigned APK. See [Verification](VERIFICATION.md) for run evidence.

Pinned inputs and clean build tasks make the procedure repeatable. The hashes identify the APKs from each run. Byte-for-byte reproducibility across machines remains unverified. Debug keys, JDK patch updates, and unlocked transitive dependencies can affect the result.

## State the actual release boundary

The settings and inset draft passed its synthetic emulator suite. Physical capture, TalkBack, GPU behavior, and capture performance require device testing. The release sequence is to check both build variants and synthetic tests first, qualify physical devices next, then validate a signed release before distributing it. The [hardware acceptance matrix](validation.md#hardware-acceptance-matrix) defines the device scenarios.
