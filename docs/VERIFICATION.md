# Lensqivo verification

## Local checks on November 19, 2026

| Check | Result |
| --- | --- |
| `node scripts/test-core.mjs` | Passed 814 assertions on Java 25.0.2 and Node 24.22.1. The Java compilation target is 17. |
| `actionlint -shellcheck= -pyflakes= .github/workflows/android.yml` | Passed using actionlint 1.7.12. External shell and Python analyzers were unavailable. |
| Manifest and resource XML | Parsed all five XML files under `app/src/main`. |
| Artifact checksum layout | A Bash fixture using the workflow's relative debug and release paths verified both hashes and detected a changed file. |
| Wrapper JAR | SHA-256 `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`, which matches the retained v9.8.2 wrapper JAR recorded in [research](research.md). |
| Clean debug/release assembly, lint and test compilation | Blocked before Gradle started. Downloading Gradle 9.9.1 returned `java.net.SocketException: Permission denied: getsockopt`. The local Android SDK and adb were unavailable. |

The full attempted command is in the [build instructions](EVALUATION.md#install-a-development-build). Hosted CI is the verification path for the new release assembly, release lint and APK artifact layout.

## Android CI evidence

| Source | Verified result |
| --- | --- |
| Main `9f3a841280d9a96a586261052d14b1b6066ccba4` | The debug build, strict debug lint and emulator jobs passed on API 29 and 35. The artifact list is empty as of November 19. [Run 41955799779](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/runs/41955799779) |
| Settings/tooling draft `7f2845ad5a2ede9b073ebc5bf3cd43423bb69280` | Both jobs passed wrapper validation, the debug build, strict debug lint and instrumentation. The logs record 814 core assertions and 32 passing instrumented tests per emulator on API 29 and 35. [Run 43273262702](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/runs/43273262702) |
| Screenshot source `a51885d315e176f639aac929b2551b527d38400b` | Preserved Compose captures of the camera, Looks and Beauty screens with a generated reference scene. [Run 41894821675](https://github.com/SHUBHRAGHAVT-57/Lensqivo/actions/runs/41894821675), [provenance](../assets/README.md) |

The draft run covers the settings row's single accessibility node, click target and disabled state on the software emulator. It predates the new release workflow. Physical cameras, TalkBack, saved-media parity, sustained recording, thermal performance, and a signed release remain pending. See the [hardware acceptance matrix](validation.md#hardware-acceptance-matrix).
