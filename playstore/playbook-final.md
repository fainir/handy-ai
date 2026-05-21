# Handy AI — Exact Steps Playbook

*Last updated 2026-05-18 (post-recovery).*

## Live state

- ✅ **v1.5.0** — APPROVED + LIVE in Closed Testing (May 16 8:13 PM, versionCode 9)
- ✅ **v1.5.1** — Built + signed at `~/Desktop/HandyAI-v1.5.1.aab` (12.8 MB)
- ✅ **TestersCommunity** — 25/25 testers active
- ✅ **Google 14-day clock** — 12 testers continuously opted in for 9+ days
- ✅ **Backend** — `cloudbot-ai.com/api/handy-key-setup/*` live + verified
- ⏳ **Production unlock** — ~May 23 (5 more days)

---

## Step 1 — Upload v1.5.1 to Closed Testing (today, ≈ 4 min)

1. Open https://play.google.com/console/u/1/developers/6923776120794608074/app/4972553077583466059/tracks/4700055656799940278
2. Click **Create new release**.
3. Drag `~/Desktop/HandyAI-v1.5.1.aab` onto the upload zone.
4. Wait ~30 s for upload. You'll see *App bundle → 10 (1.5.1) → API 30+ → Target SDK 35*.
5. Scroll to **Release notes**, paste:
   ```
   <en-US>
   v1.5.1 - Accessibility polish + tools.

   - "Read me the screen" - local on-device OCR, no API tokens
   - Named aliases: say "Mom" / "my usual" and the agent resolves them
   - Disambiguation: agent stops and asks when targets are ambiguous
   - "What can you do?" voice intent - local task library
   - Smaller direct-install APK (arm64-v8a only)
   </en-US>
   ```
6. Click **Next** → Preview & confirm.
7. Should see only the two non-blocking warnings (deobfuscation file, native debug symbols) — same as v1.5.0. **No errors expected** because the accessibility-services declaration is still on file.
8. Click **Save**, then **Go to overview**.
9. In Publishing overview, click **Send 1 change for review**.

**Expected**: approval in 1-3 days.

---

## Step 2 — TalkBack co-existence test (5 min, when Pixel is reconnected)

Critical for the blind-user audience.

1. Plug Pixel 10 into USB. Confirm with `adb devices`.
2. On the phone: Settings → Accessibility → **TalkBack** → toggle ON.
3. Same screen → **Handy AI** → toggle ON (re-grant if needed).
4. Open Handy AI. Verify:
   - TalkBack announces buttons as you focus them.
   - The mic button still works (TalkBack double-tap activates).
   - Send a small task ("set a 5-minute timer"). Verify both audio streams coexist.
5. If they conflict: file a bug. Fix in v1.5.2 would be to route Handy AI's TTS through an accessibility-specific stream.

---

## Step 3 — ~May 23: Apply for Production

1. Open https://play.google.com/console/u/1/developers/6923776120794608074/app/4972553077583466059/app-dashboard
2. Confirm the line *"12 testers have currently been opted in for **14** days continuously"*.
3. The **Apply for production** button enables. Click it.
4. Paste the listing-v1.5.md content into the production-access form.
5. Submit. Wait 1-7 days for production review.

---

## Step 4 — Post-production: pitch the disability orgs

1. **Blind Android Users podcast + Accessible Android** —
   `info@blindandroidusers.com`. Their 2025 Best-Apps episodes drove
   annual adoption.
2. **National Federation of the Blind BUILD program** —
   `outreach@nfb.org`. Pay-per-test program for blind testers.
3. **Double Tap + Mosen At Large podcasts + AppleVis Android forum** —
   largest English-speaking blind-tech reach.

Apply for **Microsoft AI for Accessibility** + **Google.org Disability**
grants to underwrite a managed free-tier for blind users who can't
manage API keys.

---

## Quick reference URLs

- Handy AI dashboard: https://play.google.com/console/u/1/developers/6923776120794608074/app/4972553077583466059/app-dashboard
- Closed Testing track: https://play.google.com/console/u/1/developers/6923776120794608074/app/4972553077583466059/tracks/4700055656799940278
- Main store listing: https://play.google.com/console/u/1/developers/6923776120794608074/app/4972553077583466059/main-store-listing
- TestersCommunity app: https://www.testerscommunity.com/app-details/lkJAGIWzZ5dhLq6NYIAB
- Landing page: https://gethandyai.app
- Setup page (paired-laptop): https://gethandyai.app/setup.html
- Demo video: https://youtube.com/shorts/v2uduk1sBkU
- Backend endpoint: https://cloudbot-ai.com/api/handy-key-setup/init

## Files on disk

```
~/Desktop/HandyAI-v1.5.0.aab            (legacy, can delete)
~/Desktop/HandyAI-v1.5.1.aab            12.8 MB — upload this next
docs/HandyAI.apk                        31 MB direct-install (v1.5.1, arm64-v8a)
playstore/accessibility-pivot.md        Full strategy
playstore/listing-v1.5.md               Store listing copy
playstore/release-notes-v1.5.1.md       Paste template
playstore/playbook-final.md             This file
```
