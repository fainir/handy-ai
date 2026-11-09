# Handy AI readiness

Android 11+ accessibility assistant. [Public Play listing](https://play.google.com/store/apps/details?id=com.claudeagent.phone), [website](https://gethandyai.app/), [source](https://github.com/fainir/handy-ai). Build and check locally with `bash scripts/ci-local.sh`.

| ID | Flow or gate | Status | Evidence or next check |
|---|---|---|---|
| H1 | Try included Claude with explicit permission and no personal key | building | Verify on Android using synthetic screen content. Verify the gateway admits the fixed profile and records spend. |
| H2 | Continue with a personal key when the trial ends | building | Verify the tenth step and the add-key handoff in chat and Easy Mode. Personal usage goes directly to Anthropic. |
| H3 | Protect the owner key and cap included usage | building | Reuse the existing Betterloop gateway and its current shared funding limits. Ten lifetime steps per Android profile, 20 per network/day, 48 across the app/day, and at most $4.75 per rolling 30 days inside existing shared caps. |
| H4 | Explain screen sharing and billing in the app, website and Play | building | Candidate website and legal pages passed 375px and 1440px. [Evidence](qa/claude-trial.md). Publish the new disclosure before the Play rollout. |
| H5 | Build, sign, and test the Android 16 update | building | Local Gradle, Android 16 system-bar and accessibility checks, app bundle signature, native library alignment and upgrade path. |
| H6 | Release via Google Play and verify the shipped version | waiting | Starts after H1–H5 pass. Check Play's target SDK, accessibility and data-safety review before marking live. |

Use the [existing guarded Claude gateway](https://github.com/fainir/betterloop/blob/main/readiness/claude-beta.md) with a server-selected model and toolset. [Google Play's AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491) governs the release disclosure. Existing Play and APK users retain their current build until a verified update ships.
