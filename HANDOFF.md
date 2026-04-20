# Handy AI — handoff

Everything I did in this session is on disk in this repo. Nothing published
yet — the last two steps (Play Console submission + hub-side endpoints) need
your accounts/credentials.

## What shipped

- **Renamed** to `Handy AI` (launcher label, a11y service label, strings).
- **New logo** — cream + ink monogram "H" with an orange dot. Vector source
  is `app/src/main/res/drawable/ic_launcher_foreground.xml` + `ic_launcher_background.xml`.
  Adaptive icon wired up.
- **Onboarding screen** — new `OnboardingActivity` at first launch. Two options
  at the bottom: **Start 7-day free trial** (opens Lemon Squeezy checkout in
  browser; user comes back and pastes the license key) OR **Use my own API key**
  (paste Anthropic key inline). State stored encrypted in `UserState` +
  `ApiKeyStore`.
- **Deferred permissions** — Accessibility is not asked for up front. On the
  first send, if it's not granted, a dialog offers "Open Accessibility settings".
- **Settings screen** — shows subscription plan / trial days left, edit/clear
  API key, re-grant Accessibility, and a new **Pair with Cloudbot** flow.
- **Cloudbot pairing (phone side)** — `HubPairActivity` requests a pair code
  from the hub, polls for claim, stores the device token. Endpoints it calls
  are documented in `playstore/hub-phone-spec.md`.
- **Release signing** — `keystore.properties` + `app/handy-ai-release.jks`
  generated with a random 24-char password. Wired into `app/build.gradle.kts`.
  `.gitignore` updated so the keystore and passwords don't leak.
- **Play Store listing content** — `playstore/listing.md`, icons, feature
  graphic, privacy policy, terms.
- **Submission walkthrough** — `playstore/submission-steps.md`.

## Build outputs ready to ship

| File | Purpose |
|---|---|
| `app/build/outputs/bundle/release/app-release.aab` | Upload to Play Console |
| `app/build/outputs/apk/release/app-release.apk` | Sideload fallback |
| `app/build/outputs/apk/debug/app-debug.apk` | Local testing only |
| `playstore/app-icon-512.png` | Play listing icon |
| `playstore/feature-1024x500.png` | Play listing feature graphic |

## Still blocked on you

1. **Play Console account + $25 fee** — I can't sign in as you. Follow
   `playstore/submission-steps.md` end-to-end; it should take ~30 minutes.
2. **Lemon Squeezy product** — `BillingConfig.kt` has a placeholder
   checkout URL. Create a subscription product with a 7-day trial at
   lemonsqueezy.com, copy the checkout URL, replace
   `LEMONSQUEEZY_CHECKOUT_URL`, rebuild. Until you do this, the subscribe
   button opens a 404.
3. **Hub endpoints** — `playstore/hub-phone-spec.md` lists the four
   endpoints your Railway service needs to add for pairing + proxy. Until
   those ship, subscription mode falls back to asking for a BYO key and
   the "Pair with Cloudbot" button will show "Hub not reachable".
4. **Privacy policy hosting** — paste `playstore/privacy-policy.md` onto
   any static page. Play Console won't accept a submission without a
   public URL.
5. **Screenshots for the listing** — see `playstore/submission-steps.md`
   section "Screenshots" for the adb one-liners once the Pixel is plugged
   back in.

## Backup now, thank yourself later

`app/handy-ai-release.jks` + `keystore.properties` are the ONLY things
that let you ship updates to users who already have the app. Back them
up somewhere outside this machine today (1Password, private GCS, a
private GitHub repo). Losing them = your Play Store listing is dead.

## Architecture map

```
OnboardingActivity ──► choose: subscribe | BYO key
                        │          │
                        ▼          ▼
                   LS checkout   enter sk-ant-...
                        │          │
                        ▼          ▼
                 paste license   save key
                        │          │
                        └────┬─────┘
                             ▼
                       MainActivity  ◄── AgentState (flows)
                        │    │    │
                    send   mic   settings
                     │
                     ▼
                attemptStartTask()
                   1. check task non-empty
                   2. check API key (prompt to Settings if missing)
                   3. check Accessibility (prompt dialog if missing)
                   4. AgentAccessibilityService.startAgent(apiKey, task)

                AgentAccessibilityService  ─► AgentLoop ─► AnthropicClient
                                                              │
                                              direct ────────┤
                                              via hub ───────┘  (future)

SettingsActivity
  ├─ Subscription (plan, manage, switch mode)
  ├─ API key (edit / clear)
  ├─ Accessibility (status + re-grant)
  └─ Cloudbot pairing ──► HubPairActivity ─► HubClient ─► hub endpoints
```

## Files added or changed in this session

Added:
- `app/src/main/java/com/claudeagent/phone/OnboardingActivity.kt`
- `app/src/main/java/com/claudeagent/phone/UserState.kt`
- `app/src/main/java/com/claudeagent/phone/BillingConfig.kt`
- `app/src/main/java/com/claudeagent/phone/LemonSqueezyClient.kt`
- `app/src/main/java/com/claudeagent/phone/HubClient.kt`
- `app/src/main/java/com/claudeagent/phone/HubPairActivity.kt`
- `app/src/main/res/layout/activity_onboarding.xml`
- `app/src/main/res/layout/activity_hub_pair.xml`
- `app/src/main/res/drawable/ic_mic.xml`
- `app/src/main/res/drawable/ic_logo_h.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/bg_logo_circle.xml`
- `app/src/main/res/drawable/bg_card_accent.xml`
- `app/src/main/res/drawable/bg_card_outlined.xml`
- `app/src/main/res/drawable/bg_chip_dark.xml`
- `app/src/main/res/drawable/bg_field.xml`
- `keystore.properties` (gitignored)
- `app/handy-ai-release.jks` (gitignored)
- `playstore/` — all listing + policy + spec content

Changed:
- `app/src/main/AndroidManifest.xml` — registered Onboarding + HubPair
- `app/build.gradle.kts` — release signing config
- `app/src/main/java/com/claudeagent/phone/MainActivity.kt` — onboarding redirect, deferred perms, mic button, action swap
- `app/src/main/java/com/claudeagent/phone/SettingsActivity.kt` — new sections
- `app/src/main/res/layout/activity_main.xml` — mic + send buttons
- `app/src/main/res/layout/activity_settings.xml` — full redesign
- `app/src/main/res/values/strings.xml` — app name + new strings
- `app/src/main/res/drawable/ic_send.xml`, `ic_launcher_foreground.xml`, `mipmap-anydpi-v26/ic_launcher{,_round}.xml`
- `.gitignore`
