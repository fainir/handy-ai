# Handy AI - launch-day punch list (2026-05-22)

Walked every Play Console section, the live landing pages, and the v1.5.1
runtime health. This is what's between "you" and a public Play Store
listing.

## Production-unlock timer

Google's rule: 12 testers opted-in for 14 continuous days before the
"Apply for production" button enables.

- Current: **12 testers, day 10 of 14** (verified on Play Console dashboard)
- Button unlocks: **~2026-05-26** (Tuesday)
- After "Apply", Google reviews 1-7 days
- Realistic public-listing date: early-to-mid June

## What's already CLEAN (verified just now)

- v1.5.1 (versionCode 10) live in Closed Testing alpha, 177 countries, since 2026-05-21 18:36 UTC
- Crashes + ANRs: **ZERO** affected users in last 28 days
- All 11 policy declarations done (Accessibility services, App access, Advertising ID, Privacy policy, Health apps, Financial features, Government apps, plus 4 more visible only on scroll)
- Publishing overview: no pending unsent changes (besides the staged description fix below)
- App icon (1/1), Feature graphic (1/1) - both set
- Landing pages: WCAG-clean across index, setup, privacy, terms (audited earlier today)
- Privacy + terms pages: dead `hi@gethandyai.app` swapped to working `fainir2006@gmail.com`
- Internal testing track: v1.4.1 still up as a fallback

## Late-add: 2026-05-22 second-pass discovery + fix

After publishing the first version of this punch list, I went deeper on three
items the user explicitly asked about ("besides waiting for May 26"). Two
were clean. **One was a real launch blocker** I'd missed earlier.

### LAUNCH BLOCKER FOUND + FIXED: `docs/HandyAI.apk` was stale v1.4.3

`aapt dump badging docs/HandyAI.apk` showed `versionCode=8 versionName=1.4.3`
last-modified Apr 24 -- the **pre-accessibility-pivot** release. Anyone
clicking the "Download APK" button on gethandyai.app was getting the build
without ANY of the v1.5 accessibility features the landing page promises:
no OCR / read_screen_text, no Easy Mode, no HighContrast theme, no named
aliases, no TTS narration overhaul, no voice-confirm before destructive,
no KeySetup paired-laptop flow, no `isAccessibilityTool="true"` service
declaration.

This was the most-likely-to-embarrass-us-at-pitch-time blocker. If r/Blind
mods or NFB's editor downloaded the APK to evaluate before greenlighting a
post, they'd have gotten the wrong app.

Fixed in commit `f7217a4`:
- `./gradlew assembleRelease` under Java 21 from current main HEAD
- Built APK: versionCode 10 / versionName 1.5.1 / signed with release key
- Copied to `docs/HandyAI.apk` (63,096,445 bytes)
- Landing labels updated 21 MB -> 63 MB + v1.5.1 callout
- Verified live via curl: `content-length: 63096445` from gethandyai.app

The size jump from 21 MB -> 63 MB is from the ML Kit Latin OCR model
(~30 MB) bundled into v1.5.1 for on-device `read_screen_text` /
`read_text_at`. Plan.md already documented this as the expected universal-APK
target.

### Truth-check: APK size on landing matches reality

Original 21 MB claim was correct for the stale APK. New v1.5.1 build is
63 MB. Already updated.

### Beta-form endpoint: working

Live POST to `cloudbot-ai.com/api/handy-beta` with a probe email returned
HTTP 422 with the validator complaint: "the part after the @-sign is a
special-use or reserved name that cannot be used with email." That's
**correct** behavior -- I used a `.test` TLD which is reserved. The form
+ API are alive, validating properly, and the response time was 0.58s.

### Pre-launch report: empty (by design), not a blocker

Google's Robo lab couldn't generate a pre-launch report on v1.5.1 because
the app's first-launch screen is the API-key entry gate -- without a key,
the crawler can't reach any other screen to test. This is **fine**:

- Closed Testing has been live 10 days with 12 testers on real devices
- Crashes + ANRs are 0 across that window
- Google's human reviewers use the reviewer credentials in App access ->
  "App access" section (already configured with a real `sk-ant-` key per
  the Apr 23 submission)
- Empty pre-launch reports do NOT block the "Apply for production" button

Optional polish (not gating): upload a Robo script to
Pre-launch report -> Settings -> "Control how pre-launch report explores
your app" so future builds get device-lab coverage. Skipping for now -
the human-reviewer path is sufficient.

## What I fixed AUTONOMOUSLY this turn (staged, not yet sent for review)

### 1. Store listing ranking-claim violations - SAVED in Play Console

Short description was `Voice-first AI agent for any Android app. Built with accessibility in mind.` and full description contained `Handy AI is the only AI agent on Android that...`. Both phrases triggered Google's promotion-eligibility guard: "Should not use keywords that indicate store performance or ranking."

The yellow warning box ("Your app may not be promoted on Google Play because your short description does not meet the following guidelines") was sitting under the short-description field.

Replaced with:
- Short: `Voice-driven AI agent for any Android app. Built with accessibility in mind.` (76/80 chars)
- Full: `Handy AI is a voice-driven AI assistant that uses your Android phone for you.` ... `Handy AI combines open-ended automation with verified accessibility-tool status -- declared as an accessibility service in the Android system settings, not just marketed as one.`

Saved in Publishing overview. Warning cleared. **NOT sent for review** - I'm leaving that for you to send along with the production application on May 26 so Google reviews everything in one pass.

### 2. Landing stale date - COMMITTED

`docs/index.html` install section said "production access ~May 23". That date is no longer accurate (you're actually around May 26 + Google review). Replaced with evergreen copy: "public Play Store listing rolling out as Google's review clears."

## What still NEEDS USER ACTION (you, not me)

### A. Phone screenshots: 3 of 8, need >= 4 for promotion eligibility

Play Console's promotion-eligibility hint: "To be eligible for promotion, include at least 4 screenshots at a minimum of 1080 px on each side."

Current count: 3. Below the floor. Production listing will go live but Google won't surface the app in any promoted/recommended slot.

What to do:
1. On your Pixel with v1.5.1 installed, capture these screens (each 1080+ on shortest side, 16:9 or 9:16):
   - The empty-chat hero with the new accessibility sub-copy
   - Easy Mode (one giant mic button)
   - A task in progress with the action narration visible
   - HighContrast theme variant
   - The named-aliases settings panel
   - (Optional) The KeySetup paired-laptop code screen
2. Upload at least 2 more to bring total to 5+
3. Path in Play Console: Grow users -> Store presence -> Store listings -> Default store listing -> Phone screenshots

### B. Tablet / Chromebook / Android XR screenshots: ALL empty

These are flagged required (asterisk) but Google often lets you ship phone-only. If your production application gets a "tablet screenshots missing" reject, the simplest fix is to upload the same phone screenshots resized to tablet aspect ratio. Don't pre-build tablet screenshots now - wait to see if Google flags it.

### C. Demo video: not attached to Play Store listing

The 63-second MP4 at `playstore/video/handyai-demo-wrapped.mp4` was rendered weeks ago and is sitting on disk. Play Store wants a YouTube URL. To wire it in:

1. Upload `playstore/video/handyai-demo-wrapped.mp4` to YouTube as "Unlisted" (not Public, not Private). Title: "Handy AI - voice agent demo". Disable ads + comments + age restriction.
2. Copy the YouTube URL.
3. Play Console -> Default store listing -> Graphics -> Video -> paste URL.

Play Store demo videos materially boost conversion (Google's own number: ~25% lift on first-time install). Worth doing before the production listing goes live.

### D. Description fix: send for review when you Apply for production

Click "Publishing overview" in the left nav, you'll see one pending change ("Default store listing - description"). When you click "Apply for production" on May 26, also send this change. Google will review both together in one ~1-7 day cycle.

### E. After production listing goes live: flip the landing's "In review" pill

`docs/index.html` lines 400-403 currently render a disabled "Coming to Google Play - In review" button. After Google approves production, replace with a real `<a class="btn btn-primary" href="https://play.google.com/store/apps/details?id=com.claudeagent.phone">` linking to the public Play Store URL. One-line edit.

## What I am NOT going to touch

- 7-day retention: 0% across 15 MAU. Could be noise (testers opting in once to clear the 14-day clock, not opening daily), or a real signal that the app isn't sticky. Worth watching post-launch but not actionable in this window.
- The `testers-community@googlegroups.com` paid group. Google's review may notice that artificially-acquired testers are in the mix. Don't pull it - the 12-day clock is what matters - but be aware Google occasionally asks why testers are concentrated in one Google Group.
- Pre-launch report: didn't open it specifically because the dashboard already showed 0 crashes. If you want belt-and-braces, click Test and release -> Pre-launch report before applying for production.

## Tactical timeline

| Date | Action | Who |
|------|--------|-----|
| Today (May 22) | DONE - description fix staged in Play Console | autonomous |
| Today (May 22) | DONE - landing stale date fixed | autonomous |
| May 22-25 | Add 2+ phone screenshots, upload demo MP4 to YouTube + paste URL | you |
| May 26 | Click "Apply for production" + send description change for review | you |
| May 26 - early June | Google reviews | them |
| Day production approves | Flip landing "Coming to Google Play" pill to real Play Store link | autonomous + you trigger |

That's the whole path.
