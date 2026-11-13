# Handy AI 1.5.8 website APK

This release combines the [reviewable first task](https://github.com/fainir/handy-ai/pull/18) with release-only R8 optimization from the earlier [build candidate](https://github.com/fainir/handy-ai/pull/15). Android 11 remains the minimum. The website keeps Google Play as its primary install link and offers the signed APK separately.

## Package checks

Local CI passed debug and release builds, Android lint, 38 JVM tests, a signed APK and a signed AAB. The APK is version 1.5.8 (18), target API 36, with the original package and release signing certificate. APK size dropped from 64,525,627 to 50,652,912 bytes and uncompressed DEX from 17,490,280 to 3,613,356 bytes. The 12 native libraries across four ABIs match the previous APK byte for byte. ZIP alignment and all 16 applicable 64-bit ELF load segments passed. The AAB contains the matching R8 mapping. The build leaves Sentry unconfigured and contains no embedded Anthropic key. [Android 16 KiB checks](https://developer.android.com/guide/practices/page-sizes).

The [website download](https://gethandyai.app/HandyAI.apk) for this release has SHA-256 `ab8a2e0f140a93631b0c1a3ba3591eebeead38ebb9499cf62ceae3163a58307e`. The old public APK was 1.5.5 (15) and used the same signing certificate.

## Android and website checks

- Android 16 installed the public 1.5.5 APK and upgraded it in place with the exact 1.5.8 release APK. The synthetic chat, the masked test key and the enabled Accessibility service survived. [Upgraded chat](website-apk-1-5-8-upgrade.png).
- On a fresh app profile at 375dp, declining the included Claude disclosure kept Start disabled. Agreeing showed the shortcut to Android Accessibility settings. After enabling and verifying the test service, the first-task button filled the editable prompt and kept the chat empty. Pressing Start created one task and returned the expected offline error. No paid model request was sent. [Signed first-task screen](website-apk-1-5-8-first-task.png).
- Phone touch and desktop keyboard both reached the live first-task guide. The Google Play button and separate APK download were visible and resolved to the expected destinations. The install upgrade used Android Debug Bridge on an isolated emulator. Browser-specific installation prompts and Play delivery of 1.5.8 require separate verification.

## Measurement

Use the existing consented `apk_clicked` event to read demand after the release. A click measures the download action. Count an outside completed screen-reading task and a seven-day return only with a support report or voluntary walkthrough. Measure Play delivery and store conversion in the separate Play cohort. [First-task guide](https://gethandyai.app/#install).
