# Handy AI 1.5.8 release candidate

November 11, 2026. Google Play flagged 0% obfuscation on 1.5.7 (17), with a February 2027 deadline. [Google’s DEX guidance](https://developer.android.com/google/play/vitals/code-optimization) lists 10 MB of uncompressed DEX as its alert threshold.

- Local CI passed debug build and lint, 38 JVM tests, the signed release APK and the signed app bundle.
- Compared with 1.5.7, uncompressed bundle DEX dropped from 17,525,636 to 3,612,332 bytes. The bundle includes its R8 mapping and retains the same 12 native library entries.
- The APK signature matches 1.5.7 and passed 16 KiB alignment. The manifest retains the package name, API 30 minimum, API 36 target, and the exact Accessibility service class and bind permission.
- Bundle SHA-256 `07168623d21b7b02876da65745bbb3a5df7fa2ed5cb6221ff8d5da998fccfce5`.
- [Submission 13](https://play.google.com/console/u/2/developers/6923776120794608074/app/4972553077583466059/publishing/submission-activity/13/details) remains in review for 1.5.7 and its 10% rollout. This candidate has no Play submission.
- Independent checks matched the APK and AAB signers, verified that the embedded mapping matches the generated file, and confirmed all 12 native libraries are byte-identical. The chat-store serializer and service names are retained.
- Android upgrade smoke remains open. The first isolated emulator exhausted the host disk. The compact retry failed during Android first-stage mount before the candidate could install. Next check is a signed 1.5.7 to 1.5.8 upgrade on a bootable Android 16 test device, preserving a synthetic chat and Settings, then opening the trial consent and personal-key screens.
