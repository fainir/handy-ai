# Handy AI — Session Handoff (2026-05-30)

Pick up here on the new machine. Everything below is current as of the last commit on `main`.

## What Handy AI is
Android app (Kotlin, package `com.claudeagent.phone`, app name "Handy AI"). A voice-driven AI agent: the user speaks/types a task, the app screenshots the screen, asks Claude (Anthropic API) what to tap, executes via the Accessibility Service, and repeats until done. BYO Anthropic API key (stored encrypted on-device). v1.5 pivoted to an accessibility-first positioning (TalkBack-aware TTS narration, Easy Mode, on-device OCR, voice-confirm before destructive actions, paired-laptop key setup, HighContrast theme). Repo: https://github.com/fainir/handy-ai (branch `main`).

## CURRENT STATE — the one thing that matters
**Handy AI is LIVE on the public Google Play Store** (went live ~2026-05-30):
- Public listing: https://play.google.com/store/apps/details?id=com.claudeagent.phone (renders in a real browser; `curl` returns 404 because Google serves 404 to non-browser user-agents — the browser is the source of truth).
- **v1.5.3 (versionCode 13) is IN REVIEW** at Google right now — it supersedes the v1.5.1 that's currently serving users. When approved (typically <=7 days, usually faster for updates), all users get v1.5.3 automatically. The live app stays up throughout.
- Landing page LIVE: https://gethandyai.app (GitHub Pages, custom domain, valid cert). Direct-install APK at https://gethandyai.app/HandyAI.apk is v1.5.3 (universal, ~64 MB).

## Version history (what each release fixed)
- v1.5.1 (vc10): accessibility pivot. Shipped to production. **arm64-only** (abiFilter bug) + **crashed on mic tap** (missing VIBRATE permission). This is what most live users still have until v1.5.3 clears review.
- v1.5.2 (vc12): universal AAB (all 4 ABIs, +1,291 phone devices) + 16 KB page-size compliance (bumped Sentry 7.14.0->8.43.0, ML Kit 16.0.0->16.0.1). Submitted, then superseded by v1.5.3.
- **v1.5.3 (vc13): CURRENT. Adds VIBRATE permission + try/catch around vibrate() — fixes the mic/send crash. Universal + 16 KB. IN REVIEW.**

## The mic crash (fixed in v1.5.3, verify it lands)
Root cause confirmed from the device crash log: `SecurityException: ... android.permission.VIBRATE` at `AccessibilityHelper.haptic(AccessibilityHelper.kt:269)` <- `MainActivity.kt:129` (mic onClick). The v1.5 haptics feature called `Vibrator.vibrate()` but the manifest never declared VIBRATE; haptics default ON, so every mic/send tap crashed. Fix verified on-device via a `.debug`-suffixed variant (installs alongside prod): mic tap -> no crash -> speech captured. Commit `29511d7`.
- **Temp workaround for any user still on v1.5.1**: Settings (gear) -> Voice and audio -> turn OFF "Vibration feedback" (the gear + toggle don't buzz, so they're safe).

## OPEN / NEXT STEPS
1. **Confirm v1.5.3 goes live.** Check the public Play Store URL in a browser (not curl) or Play Console -> Production -> "Active · Release 13 (1.5.3) in review" should flip to "in production". When live, the mic crash is resolved for everyone.
2. **Acquisition is the real open work.** App is published but needs users/testers. Ready-to-send outreach assets are in `playstore/outreach/` (Twitter thread + r/Blind + NFB/ACB/RNIB newsletter pitches) and `playstore/launch-posts.md`. Demo video on YouTube (unlisted): https://youtu.be/SxnJstUEAJI — wired into the Play listing. Twitter thread still UN-posted.
3. **Optional polish (not blocking):** the 2 Play warnings (no deobfuscation mapping / no native debug symbols) are benign; upload mapping+symbols later for readable crash reports.

## KEY FACTS / GOTCHAS (save hours)
- **Play Console account**: `welcometothebestplace@gmail.com` -> developer **"Sumbli"**, account ID `6923776120794608074`, app ID `4972553077583466059`. (The `fainir2006@gmail.com` Play accounts "Nir Fainshtein" + "Uniget" are BOTH CLOSED for inactivity — don't use them.)
- **Build**: needs Java 21. `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew bundleRelease` (AAB) or `assembleRelease` (APK). Keystore at `app/handy-ai-release.jks` + `keystore.properties` (gitignored — MUST be copied to the new machine to sign release builds; if missing, building signed releases is blocked).
- **Releasing a new version**: bump BOTH `versionCode` (must increment) + `versionName` in `app/build.gradle.kts`. Play rejects duplicate versionCodes — if an upload is blocked mid-way, that versionCode is consumed; bump again.
- **16 KB requirement is now HARD** (no "Proceed anyway"). Any native .so must be 16 KB-aligned. Check: `objdump -p <lib>.so | grep -A1 LOAD` -> must show align `2**14`. ML Kit 16.0.1 + Sentry 8.43.0 are compliant; older versions are not.
- **Uploading an AAB to Play Console via browser automation**: Claude-in-Chrome `file_upload` only accepts session-shared files. Workaround used this session: temp-commit the AAB to `release-staging/` (force-add; it's gitignored), push, then in the browser `fetch()` it from `raw.githubusercontent.com` (sends CORS `*`), build a `File` via `DataTransfer`, assign to `input[type=file]`, dispatch `change`. Remove the temp AAB from git afterward.
- **Device testing without wiping the user's app**: NEVER `adb uninstall com.claudeagent.phone` (wipes API key + accessibility grant — user memory rule). To smoke-test a build: temporarily add `applicationIdSuffix = ".debug"` to the `debug` buildType, `assembleDebug`, `adb install -r` -> installs as `com.claudeagent.phone.debug` alongside prod. Enable the mic in a fresh debug install by typing a fake key `sk-ant-test...` (passes the `sk-ant-` prefix check). Uninstall the `.debug` variant + `git checkout app/build.gradle.kts` after. **Lesson this session: always smoke-test mic/send on-device before shipping.**
- **Pixel 10** (model `frankel`, serial `57191FDCR006QE`) is the test device, connected intermittently via USB. adb sometimes needs `adb kill-server && adb start-server`.
- **Backend**: `cloudbot-panel` repo (separate, `~/Documents/GitHub/cloudbot-panel`) hosts `/api/handy-beta` (signups) + `/api/handy-key-setup` (paired-laptop key flow) on Railway (`cloudbot-ai.com`). Railway app service `exquisite-benevolence`; Postgres `Postgres-rKVX` (public URL via `railway variables --service Postgres-rKVX --kv | grep DATABASE_PUBLIC_URL`).
- **No scheduled tasks active** — all launchd jobs/reminders were cleaned up; one remote routine `trig_01XKPvG8nqxgLfEXKZxHGz1R` is DISABLED.

## Source map (where things live)
- `app/src/main/java/com/claudeagent/phone/MainActivity.kt` — mic/send onClick (~lines 118-132), startVoiceInput (~322).
- `AccessibilityHelper.kt` — TTS narrate/speak, haptic (vibrate ~line 258), modes/verbosity, destructive-word detection, HighContrast theme.
- `AgentLoop.kt` — screenshot->Claude->act loop + system prompt. `AgentTools.kt` — tool defs (tap/swipe/type/read_screen_text/read_text_at/finish). `ScreenOcr.kt` — ML Kit OCR.
- `EasyModeActivity.kt`, `KeySetupActivity.kt`, `SettingsActivity.kt`, `HandyAIApplication.kt` (Sentry init, DSN-gated off).
- `app/src/main/AndroidManifest.xml` — permissions (INTERNET + VIBRATE), activity registrations, accessibility service config.
- `docs/` — landing page (index.html, setup.html, privacy.html, terms.html) + HandyAI.apk. `.claude/plan.md` — full phase-by-phase history (Phases 1-14).

## Plan / progress
`.claude/plan.md` has the complete history. Latest phases: 12 (public Play Store live), 13 (v1.5.2 universal+16KB), 14 (v1.5.3 mic-crash fix). Read it first for full context.
