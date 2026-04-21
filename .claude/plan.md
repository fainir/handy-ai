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
- [~] Production hardening sprint (XL)
  - DoD: landing-page APK matches current monochrome build; server-side trial gate so `pm clear` can't reset the clock; real Lemon Squeezy product + checkout URL; Resend SMTP on Supabase (rate limit lifted); Sentry hooked up.
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
- [ ] Add Phone + PhonePairCode models in cloudbot-panel
- [ ] /api/phone router: pair/init, pair/status, pair/claim, list
- [ ] Register router + deploy to Railway
- [ ] Smoke-test production endpoints

## Phase 3: Play Store [blocked on Google verification]
- [ ] Google approves ID upload (1-7 days)
- [ ] Create app, upload .aab, fill listing, submit
