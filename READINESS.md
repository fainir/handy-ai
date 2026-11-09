# Handy AI readiness

Android 11+ accessibility assistant. [Public Play listing](https://play.google.com/store/apps/details?id=com.claudeagent.phone), [website](https://gethandyai.app/), [source](https://github.com/fainir/handy-ai). Build and check locally with `bash scripts/ci-local.sh`.

| ID | Flow or gate | Status | Evidence or next check |
|---|---|---|---|
| H1 | Try included Claude with explicit permission and no personal key | verified | [Live gateway and signed Android acceptance](qa/claude-trial.md). Declining left the composer disabled. Opting in unlocked it. The final build ran the on-device OCR tool and returned the correct screen title on the second Claude step. |
| H2 | Continue with a personal key when the trial ends | verified | [Quota and Android checks](qa/claude-trial.md). Database tests refuse the eleventh step. The native transport handles zero remaining. Android Settings saved and masked a synthetic key, stopped included mode, and cleared the fixture. Personal usage goes directly to Anthropic. |
| H3 | Protect the owner key and cap included usage | verified | [Live deployment, spend readback and database tests](qa/claude-trial.md). Ten lifetime steps per Android profile, 20 per network/day, 48 across the app/day, and at most $4.75 per rolling 30 days inside existing shared caps. |
| H4 | Explain screen sharing and billing in the app, website and Play | waiting | [The Android prompt and candidate website](qa/claude-trial.md) passed. The website source is staged for Pages. Update Play's Data safety and Accessibility declarations before the public rollout. |
| H5 | Build and sign the Android 16 update | verified | [Local CI and package evidence](qa/claude-trial.md). 38 JVM tests passed. Signed APK and bundle use the existing signer. APK and native ELF alignment checks passed. |
| H6 | Put the signed candidate on Google Play's internal track | owner | Owner action 1. Use version 1.5.7, code 17. The public track is unchanged. |
| H7 | Promote and verify the release through Google Play | waiting | After internal testing, complete the updated Data safety and Accessibility declarations and verify the reviewed public version, then update the direct APK if appropriate. |

## Owner actions

1. [Google Play Console](https://play.google.com/console/) → Handy AI → Internal testing → upload the [signed 1.5.7 bundle](app/build/outputs/bundle/release/app-release.aab). This enables Play-distributed device verification. Keep the release on the internal track during verification.

Use the [existing guarded Claude gateway](https://github.com/fainir/betterloop/blob/main/readiness/claude-beta.md) with a server-selected model and toolset. [Google Play's AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491) governs the release disclosure. Existing Play and APK users retain their current build until a verified update ships.
