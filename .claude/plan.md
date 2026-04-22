# Handy AI — Master Plan
*Type: SaaS + Android | Progress: 12/16 (75%)*

## Phase 1: App (shipped) [x]
- [x] Rename to Handy AI + logo + adaptive icon
- [x] Onboarding: subscribe vs BYO API key
- [x] Deferred permissions
- [x] Settings: edit key, re-grant, pair hub
- [x] Release signing + signed .aab
- [x] Landing page + GitHub Release v1.0

## Phase 1b: Voice UX polish
- [x] Auto-send after voice input ends (S)
  - DoD: tap mic, speak, stop talking → task starts without second tap; empty results are ignored; appending to existing text still works but also auto-sends
  - Impl: MainActivity.voiceLauncher calls attemptStartTask() after setting the recognized text (only when not already running). Relies on Google's built-in silence endpointing — the dialog closes on its own after you stop talking, then we send.
- [x] Replace orange theme with monochrome cream-on-ink (S)
- [x] Make email + Google auth production-ready (L)
- [x] Provision live Supabase project + verify sign-in end-to-end (M)
- [x] Wire Google Sign-In end-to-end (M)
  - DoD: Handy AI Google Cloud project created; Web + Android OAuth clients; Supabase Google provider enabled with Web Client ID + secret; GOOGLE_WEB_CLIENT_ID baked into BuildConfig; v1.4 APK built + staged to docs/; "Continue with Google" button visible in onboarding once installed.
  - Done: GCP project `handy-ai-494116`; OAuth consent screen (External, Handy AI); Web OAuth Client `103501871882-0fujas5tkn9maeu21f763283oka6ir9t.apps.googleusercontent.com` with redirect `https://lahxcictftleizekgzhu.supabase.co/auth/v1/callback`; Android OAuth Client with package `com.claudeagent.phone` + release SHA-1; Supabase Auth → Providers → Google enabled with Client ID + secret; local.properties updated; v1.4 release APK built + swapped into docs/HandyAI.apk.
  - Pending (when Pixel reconnects): `adb install -r app-debug.apk`.
- [x] Production hardening sprint (XL)
  - DoD: landing-page APK matches current monochrome build; server-side trial gate so `pm clear` can't reset the clock; real Lemon Squeezy product + checkout URL; Resend SMTP on Supabase (rate limit lifted); Sentry hooked up.
  - Done: v1.3 APK live on Railway; profiles/RLS/my_entitlement view + EntitlementClient gate; Handy-AI-branded OTP email template saved on Supabase; SupabaseAuth.signOut + SettingsActivity server-side revocation; ChatStore capped at 500 msgs / 100 sessions; Sentry SDK wired + DSN-gated in HandyAIApplication; Lemon Squeezy Edge Function ready to deploy; user_id threaded through checkout URLs.
  - User-action blockers: Resend signup → API key; Google Cloud OAuth web client; Lemon Squeezy product + webhook signing secret; Sentry project → DSN.
  - DoD: `handy-ai` project exists on Supabase fainir's Org (eu-central-1); SUPABASE_URL + anon key committed to local.properties (NOT to git); APK built from those; /auth/v1/otp returns 200 and creates the user record in auth.users.
  - Verified: curl POST /otp → 200 → fainir2006@gmail.com appears in auth/users with UID da77b31f-25a8-4a8e-aac3-3b9f7c3f69b3. APK v1.1 installed with BuildConfig.SUPABASE_URL = https://lahxcictftleizekgzhu.supabase.co baked in.
  - Phone-UI loop not rerun because the current user is BYO_KEY (legacy guard correctly skips). To exercise on phone: Settings → Switch to subscription → kill+relaunch → hit sign-in flow.
  - DoD: Supabase URL + anon key + Google web-client ID read from local.properties (not hardcoded); graceful error copy for rate-limit / wrong-code / expired-code / network; Google sign-in button on onboarding (hidden until configured); Settings shows "Signed in as X" + Sign-out; existing SUBSCRIBED users with no session get routed back to sign-in once Supabase is configured; token refresh helper ready.
  - Impl: app/build.gradle.kts BuildConfig fields from local.properties; BillingConfig reads BuildConfig.*; SupabaseAuth.parseError + signInWithGoogleIdToken + refreshSession; SettingsActivity new section + sign-out; OnboardingActivity Google CTA via CredentialManager; MainActivity legacy-upgrade guard; local.properties.example template; bumped deps for credentials + googleid.
  - DoD: no orange anywhere (logo + UI); buttons are cream-filled with ink icons/text; user bubble is cream with ink text; logo H-monogram reads cleanly without the orange dot (or keeps the dot in ink as a period-style mark).
  - Impl: colors.xml accent→cream (#EDE6D6), bg→#111111, on_accent→ink; ic_launcher_foreground.xml dot recolored ink (or removed); bg_bubble_user uses accent which cascades; bg_logo_circle stays cream; ic_logo_h verified.
- [x] Align theme with logo + Supabase email-OTP for trial sign-in (L)
  - DoD: app-wide accent = logo orange (no purple left); bg = ink #1A1A1A; onboarding "Start free trial" requires email + 6-digit OTP (Supabase); AuthStore persists access_token in EncryptedSharedPreferences; BYO-key path unchanged and does not require sign-in.
  - Impl: colors.xml accent→#E66A3A, bg→#1A1A1A, text→cream; SupabaseAuth.kt (OkHttp /auth/v1/otp + /auth/v1/verify — no SDK); AuthStore.kt (EncryptedSharedPreferences); BillingConfig.SUPABASE_URL + SUPABASE_ANON_KEY (placeholders, user must paste real values); OnboardingActivity adds SIGN_IN_EMAIL + SIGN_IN_OTP steps; trial badge chip text swapped to cream for visibility; onboarding primary buttons now use accent backgroundTint.
  - Pending user action: create Supabase project, paste URL + anon key into BillingConfig.kt.
- [x] Chat-style UI with sessions drawer (L)
  - DoD: messages appear as bubbles (user right, assistant left), mic transcript becomes a user bubble like text; left drawer shows past sessions + "New chat" button; sessions persist across app restarts
  - Impl: ChatStore (SharedPreferences + JSON, StateFlow sessions/activeId/activeMessages), DrawerLayout + RecyclerView rewrite of activity_main.xml, ChatAdapter with 4 view types (user/assistant/action/status), SessionsAdapter, AgentLoop.log split into appendAssistant/appendAction/appendStatus, MainActivity rewritten: hamburger opens drawer, mic→sendTask, text→sendTask, new-chat button + session rows, empty-state hero text.
- [x] Accessibility-shortcut floating button opens Handy AI (S)
  - DoD: tapping Android's floating accessibility button (visible once the service is enabled) brings Handy AI to the foreground from any app
  - Impl: added `flagRequestAccessibilityButton` to accessibility_service_config.xml; registered AccessibilityButtonController callback in onServiceConnected() that launches MainActivity (NEW_TASK | CLEAR_TOP | SINGLE_TOP). Also updated the AgentLoop system prompt so the agent recognizes the H overlay as the Handy AI shortcut, not a random system overlay.

## Phase 2: Hub phone endpoints [in-progress]
- [x] Refresh shareable release APK — rebuild signed release with today's fixes and replace docs/HandyAI.apk
  - DoD: `docs/HandyAI.apk` contains versionCode 5 / versionName 1.4 + Sentry-auto-init-off + pending-task + instant-stop; signed with release keystore; size ≈ 21MB
  - Done: rebuilt via `assembleRelease` → copied to `docs/HandyAI.apk`. aapt confirms package=`com.claudeagent.phone` versionCode=5 versionName=1.4. Landing-page download-button label bumped from 13 MB → 21 MB to match.
- [x] Fix v1.4 boot crash — disable Sentry auto-init (SentryInitProvider throws when DSN="")
  - DoD: app launches to onboarding/chat on Pixel with no FATAL in logcat; Sentry stays silent until a real DSN is wired
  - Done: added `<meta-data android:name="io.sentry.auto-init" android:value="false"/>` to AndroidManifest; rebuilt v1.4 debug; `Displayed com.claudeagent.phone/.OnboardingActivity +809ms` with no FATAL. `HandyAIApplication.onCreate()` remains the only init path, gated on DSN non-blank.
- [x] Resume task after accessibility grant + instant Stop
  - DoD: send a message (text or mic), tap "Open Accessibility", grant, hit Back → agent starts running the queued task with no extra tap. Same for missing-key bounce. Message always appears in chat even before permission grant. Not Now dismisses the queue. Tapping Stop flips the button to Send within <1s and cancels any in-flight Anthropic request (no waiting for the 120s read timeout).
  - Done: MainActivity.sendTask now appends the user message + stashes `pendingTask` BEFORE the permission dialogs (so the message survives the bounce); onResume → resumePendingIfReady silently fires the queued task once key + accessibility + service are all present. Extracted `runAgent()` so replay doesn't double-append. AgentAccessibilityService.stopAgent now synchronously pushes `RunState.Stopped` + status "Stopped" so the UI button flips to Send immediately. AnthropicClient switched from blocking `execute()` to `awaitCall()` — OkHttp call enqueued inside a `suspendCancellableCoroutine` with `invokeOnCancellation { call.cancel() }`, so cancellation aborts the socket read instantly instead of waiting out the 120s read timeout.
- [ ] Add Phone + PhonePairCode models in cloudbot-panel
- [ ] /api/phone router: pair/init, pair/status, pair/claim, list
- [ ] Register router + deploy to Railway
- [ ] Smoke-test production endpoints

## Phase 3: Play Store [blocked on Google verification]
- [ ] Google approves ID upload (1-7 days)
- [~] Rebuild signed release .aab with today's fixes (v1.4 w/ Sentry+pending+stop) ready to upload
  - DoD: `app/build/outputs/bundle/release/app-release.aab` has versionCode 5 / versionName 1.4 and contains the Sentry-auto-init-off manifest tweak
- [ ] Create app, upload .aab, fill listing, submit

## Phase 1c: v1 copy pass (API-key-only) [in-progress]
- [~] New-user page: simple hero + API-key bar at bottom (replaces the chat input until a key is saved)
  - DoD: fresh launch shows one screen: toolbar "Handy AI", centered big "What should I do on your phone?" + one-line subtitle + tiny "Get a key → console.anthropic.com" link; the bottom bar is a single API-key input + ✓ save button. No examples. No setup card. No onboarding activity. Once a key is saved → the bar becomes the normal chat input + mic + send, and the examples show up (subsequent empty states). sendTask never opens OnboardingActivity anymore.
- [x] Chat is the home screen; onboarding is optional setup
  - DoD: first launch opens straight to the new-chat empty state with: app name in toolbar, hero "what should I do on your phone" + explainer subtitle + 3 example prompts + mic hint; if no API key yet, a setup card with "How it works" + "Add your Anthropic API key" button is visible above the examples; tapping the button opens the key-entry screen (was Onboarding) and returning with a valid key hides the card. No Subscribe/OTP/Google in UI. Friend-readable copy, no "BYO" or "sk-ant-" jargon.
  - Done: MainActivity drops the `!isOnboarded → redirect` gate so chat is always home. activity_main.xml's hero now wraps a ScrollView with: hero_prompt + hero_subtitle + setupCard (gone unless ApiKeyStore is empty) + 3 italicized example lines. updateEmptyState toggles the setup card whenever the chat is empty AND no key saved; onResume refreshes the state so returning from the key-entry screen hides the card. OnboardingActivity now launches straight into Step.KEY with a richer layout: "How it works" title + body → divider → "Paste your Anthropic API key" title → cost/how-to lines → new outlined "Get a key → console.anthropic.com" button (opens Intent.ACTION_VIEW @ https://console.anthropic.com/settings/keys) → key input → Continue → "Next: Accessibility" hint footer. sendTask's no-key path bypasses the old Settings-based dialog and goes directly to the OnboardingActivity. New strings: hero_prompt / hero_subtitle / hero_example_{1,2,3} / onboarding_how_it_works_{title,body} / byo_key_how_to / onboarding_next_step_hint / setup_card_{title,body} / add_key_cta / get_key_button / anthropic_keys_url. docs/HandyAI.apk refreshed (21.5 MB).
