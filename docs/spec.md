# Lensqivo — first working version

An original Android camera for everyday photos and short films. A quiet charcoal body, warm paper text, amber controls, and a viewfinder that remains the main event. Lensqivo (Lens-kee-vo) combines Lens for photography and optics, Q for quality, and Vivo for vivid, lifelike imagery. The name was chosen by the project owner; no trademark clearance is claimed.

## Product decisions

- Native Kotlin / Compose, Android 10+ (API 29). CameraX owns camera sessions, JPEG output, recording, audio and MP4 finalization.
- Four original color looks, variable strength, and an explicit original/processed comparison. These are creative color transforms, not claims to reproduce proprietary film stocks.
- Photo and video bind separately. Prefer high quality JPEG and FHD video with supported lower-quality fallback; never manufacture encoder dimensions or promise an unsupported frame rate.
- One Media3 RGB transform is shared by preview and output. CameraX ViewPort defines the crop. Shared rendering is an implementation choice; perceptual parity and actual output resolution still require hardware validation.
- One capture transaction at a time. Stop is not completion: reconfiguration stays locked until Finalize. Leaving the foreground requests stop. No background service, network permission, advertising, analytics, account, location, or broad media permission.
- Microphone permission is requested only on opt-in. Denial leaves silent video available. Settings are a sheet; they never remove the camera screen.
- Display only actual supported zoom and exposure ranges. Focus uses PreviewView's metering-point factory. Saved media opens through a temporary read grant.
- Persist look, strength, grid and audio preference locally. No external filter parser in version one.
- Accessible labels/state, 48dp controls, scalable text, scrolling controls on short windows, and adaptive portrait/landscape composition.

## Completion contract

Deliver an independent local Git project, runnable core tests, Android application code, instrumented UI tests, build/CI configuration, and honest verification documentation. A phone-ready release requires a successful Android build and device checks. Do not represent a design preview as camera execution.

## Deliberate exclusions

RAW video, Log/HDR, manual sensor controls, third-party LUT import, cloud sync and subscriptions are outside this first version. Preserve a clear route to original capture if the effect pipeline fails; never silently save an unfiltered photo while displaying a filtered preview.
