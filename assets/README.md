# Lensqivo artwork

## Native interface captures

`ui-camera-sample.jpg`, `ui-looks-sample.jpg` and `ui-beauty-sample.jpg` are native Compose UI captures from the isolated API 35 emulator in [run 41894821675](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/runs/41894821675), source `a51885d315e176f639aac929b2551b527d38400b`. They use production camera chrome and creative drawers around the generated reference still life. The test supplies a synthetic camera state and image in place of a hardware camera feed. These are interface evidence, with no camera-quality or hardware-performance claim.

The test captures the real Compose rendering, resizes to 400 pixels wide, and encodes JPEG at quality 80. Files were decoded directly from numbered CI log chunks, visually inspected, and copied without repainting or retouching. All scenes are generated reference artwork; no personal photographs or device screenshots are included.

## Generated reference scene

The in-app `app/src/main/assets/samples/look-reference.jpg` is a separate AI-generated, original reference still life (peaches, ivory cup, blue cloth and window light), created with the built-in image-generation tool on 4 November 2026. Its 240 × 320 JPEG is used only to illustrate actual color transforms in sample thumbnails. It contains no user photography and is not evidence of Lensqivo camera quality.

Reference-image prompt: "Create a single original photorealistic reference photograph for tiny camera color-look thumbnails in Lensqivo. Portrait 3:4 composition. Sunlit still life: three ripe peaches with one green leaf, an ivory ceramic cup, small deep blue cloth on a warm pale stone table by a window. Natural neutral sRGB-style photographic color, broad soft window light, gentle real shadows and subtle texture. Focus sharp on peaches and cup, background quiet and light gray. Include warm skin-like peach tones, blue, green, neutral ivory, no writing no logo no border no collage. This is a sample image for users to compare color filters, not an app screenshot or claim about camera performance. No people."

`lensqivo-cover.jpg` is original AI-generated brand artwork created for this repository with the built-in image-generation tool on 4 November 2026. It is not an in-app screenshot, a camera sample, or evidence that the implemented looks reproduce its rendering.

The generated original is retained locally. The repository copy is a JPEG export at the original 1983 × 793 dimensions, quality 88, approximately 346 KiB, inspected after export for readable text and composition. No third-party camera logos, film-stock assets or device mockups are included.

## Generation prompt

Create an original wide editorial cover image for the public GitHub README of Lensqivo, an Android camera app for considered live color and everyday photography. Aspect ratio 2.5:1, landscape, high resolution. Art direction: premium independent photography publication, tactile rather than glossy tech. Deep near-black olive charcoal left third with ample breathing room and crisp elegant warm-ivory serif wordmark spelled EXACTLY 'Lensqivo' in large readable letters, and underneath only the small text 'LIGHT. COLOR. FEELING.' in spaced sans-serif. Right two thirds: a beautiful original photographic composition of sunlit pale stucco architecture, an arched doorway, olive tree shadows, warm late-afternoon amber light. Three subtly separated vertical photographic panels interpret the same scene in warm amber, restrained cool dusk, and silver monochrome. Clean geometry, photographic realism, natural texture, restrained fine grain, warm-paper palette, slight print texture. The typography must remain clearly readable when this cover is scaled down on GitHub. No device mockups, no app UI screenshots, no fake product screenshots, no camera hardware branding, no copyrighted film logos, no feature lists, no people, no watermarks. This is brand artwork, not evidence of camera output.
