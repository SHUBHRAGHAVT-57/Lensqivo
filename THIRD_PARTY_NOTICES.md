# Ownership and third-party notices

The [MIT license](LICENSE) covers original Lensqivo source and documentation owned by Shubh Raghav. Third-party components retain their own licenses, authorship and notices. Other contributors retain their rights.

| Component | Ownership and terms |
| --- | --- |
| Gradle wrapper JAR and launchers | Gradle and the original authors, Apache License 2.0. The JAR includes `META-INF/LICENSE`. The launchers retain the upstream copyright and license headers. [Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE) |
| AndroidX Compose, CameraX, lifecycle, activity, core, ExifInterface, Media3 and test libraries | Android Open Source Project and upstream contributors. Retain the notices and licenses shipped with the resolved dependencies. [AndroidX license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Kotlin and its Compose compiler plugin | JetBrains and contributors, Apache License 2.0. [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| Android SDK and JDK | Build prerequisites distributed under their respective upstream terms. |
| Artwork and interface captures | The code license covers source and documentation. The [assets guide](assets/README.md) records the provenance of the generated artwork and the interface captures. |

CameraX and Media3 supply the capture, recorder and rendering infrastructure used by Lensqivo. When redistributing an APK, retain the license notices of its resolved dependencies.
