# Handy AI — Accessibility-First Repositioning

*Drafted 2026-05-12. Updated 2026-05-18 after recovery from lost worktree.*

## Strategic framing

Handy AI is *already* the most powerful accessibility tool on Android,
and almost nobody knows it.

Existing accessibility apps fall into two camps:
- **Describe-only**: Seeing AI, Be My Eyes/Be My AI, Lookout, Envision —
  tell you what's on screen, but can't act.
- **Pre-canned actions**: Voice Access, Action Blocks, Siri Shortcuts —
  execute a fixed list of commands, can't handle novel tasks.

Nobody can say *"order my usual DoorDash"* or *"DM Sarah I'm running
late"* and have a phone actually do it for them. That's exactly what
Handy AI does. The Accessibility API was *literally designed for
disability use cases* — we're using it for AI automation, but the
audience it was built for is right there.

### 🚨 THE FINDING THAT CHANGES EVERYTHING

**Google Play policy explicitly prohibits open-ended AI agents from
using the Accessibility API UNLESS the app is verified
`isAccessibilityTool="true"`** (Play Console Help — Use of the
AccessibilityService API).

This means:
1. Handy AI's *current* positioning as a general AI agent is at
   policy risk. Google could pull the app for "autonomously
   initiating, planning, and executing actions" via the Accessibility
   API without an accessibility-tool justification.
2. The accessibility pivot is no longer just market expansion — **it
   is the only legal path** for what Handy AI already does on Android.
3. Once verified as `isAccessibilityTool="true"`, Handy AI inherits a
   **regulatory moat**: every general-purpose "Claude-for-phone" or
   "Gemini-for-phone" wrapper is locked out of this capability.
4. Gemini's agentic mode (Feb 2026) sidesteps this by operating in a
   *virtual window* against a partner allowlist (DoorDash, Uber,
   Grubhub) and stopping before final confirmation — they can't touch
   arbitrary apps because they're not an accessibility tool.

### The pivot

**The pivot is positioning, not rebuilding.** Same product, two
audiences:
1. *"For everyone"* — power users, drivers, multitaskers.
2. *"For users with vision and motor disabilities"* — the underserved
   accessibility market AND our regulatory shield.

---

## User personas

### P1 — Maya (blind, 34, TalkBack power user)
Uses TalkBack 100% of the time. Frustrated by linear screen-reader
navigation: 12 swipes to send a text, 30 swipes to order food. Has
used Be My AI for "what is this label" but it can't *do* things.
**What she wants**: speak a task → it runs → speak the result back.

### P2 — Ron (low vision, 67, retinitis pigmentosa)
~10% central vision left. Uses 4x font scaling + dark mode. Can
technically use a touchscreen but it's exhausting. Cares about audio
confirmation before any destructive action.

### P3 — Jordan (motor disability, 28, ALS)
Can speak clearly but limited fine-motor control. Single-tap OK,
swipe/precise touch is hard. Currently relies on a caregiver for
messaging and ordering food. Independence is the goal.

### P4 — Eleanor (cognitive disability, 72, mild dementia)
Can speak naturally but gets confused by app UIs. Forgets which app
does which thing.

### P5 — Sam (abled, 31, driver/parent)
Drives 90 min/day. Has young kids. Hands often busy. Wants voice
everything *when* their hands are full, but normal app use otherwise.

### P6 — Priya (abled, 26, productivity nerd)
Wants to delegate annoying multi-step phone tasks. Cares about speed
and reliability over voice. Won't use a "disability-coded" product
if it feels limiting.

**Implication**: accessibility features should be **opt-in toggles
that escalate**, not the default UI.

---

## Feature tiers shipped in v1.5.0 + v1.5.1

### Tier 0 — Regulatory (v1.5.0)
- `android:isAccessibilityTool="true"` declared in manifest
- Updated `accessibility_service_description` to reflect a11y positioning
- Play Console accessibility-services declaration filed with Motor +
  Vision + Cognitive disabilities + demo video URL

### Tier 1 — Audio I/O (v1.5.0)
- Text-to-speech reads every assistant reply (auto-on if TalkBack detected)
- TTS narrates actions (verbosity-gated: off / brief / detailed)
- Voice-confirm before destructive actions (Send, Buy, Delete) with 1.5s pause
- Haptic feedback (TASK_STARTED, TASK_DONE, NEEDS_INPUT, ERROR, TICK)
- TalkBack auto-detect via `AccessibilityManager.getEnabledAccessibilityServiceList`
- Bluetooth headset routing (via system audio)

### Tier 2 — Removing visual reliance (v1.5.0 + v1.5.1)
- **Paired-laptop API key setup** (v1.5.0): blind users get a 6-char
  code spoken aloud + NATO phonetic spelling, enter on
  gethandyai.app/setup on their laptop, paste key via desktop screen
  reader, phone receives via short-lived HTTPS poll
- **OCR via MlKit text recognition** (v1.5.1): `read_screen_text` and
  `read_text_at` agent tools; local, free, instant — saves Anthropic
  tokens for "read me the menu" / "what does this notification say"
- **High-contrast theme** (v1.5.0): pure white on pure black, WCAG AAA
- Honor system font scale

### Tier 3 — Cognitive load (v1.5.0 + v1.5.1)
- **Easy Mode** (v1.5.0): one giant 240dp mic button, no chat history,
  no menus, force-on TTS
- **Common-task library** (v1.5.1): "what can you do?" voice intent
  returns a curated list of representative tasks personalized to
  installed apps. Resolved locally, no API tokens.
- **Named aliases** (v1.5.1): Mom / the babysitter / my usual →
  user-defined map, injected into agent system prompt

### Tier 4 — Trust & safety (v1.5.0 + v1.5.1)
- **Audit-log voice intent** (v1.5.0): "what did you do?" reads back
  last 12 actions
- **Disambiguation rule** (v1.5.1): agent stops and asks instead of
  guessing when multiple plausible targets exist (multiple "Bank"
  apps, multiple "Mom" contacts)
- `isAccessibilityTool="true"` declared (Tier 0)

### Tier 5 — Deferred
- Wake word ("Hey Handy") — needs Picovoice license
- Notification triage — needs separate Play approval for
  NotificationListenerService
- Family/caregiver remote setup — v1.7+
- Voice PIN / voiceprint auth — defer until $$ flows exist
- Multi-language voice + TTS — defer until validated

---

## Landing page changes (deployed to gethandyai.app)

- Dual hero: "Talk to your phone. It does the rest. — For everyone,
  designed with vision and motor accessibility in mind"
- New "Designed for accessibility" section with 6 cards
- Skip-to-content link, `:focus-visible` rings, semantic `<main>`,
  ARIA labels, `prefers-reduced-motion` + `prefers-contrast` honored
- `setup.html` for the paired-laptop API key flow
- Testimonials section (honest empty-state until real quotes)

---

## Play Store listing changes (sent for review, approved 2026-05-16)

- **Short description**: "Voice-first AI agent for any Android app.
  Built with accessibility in mind."
- **Full description**: two-section structure ("FOR EVERYONE" +
  "BUILT FOR ACCESSIBILITY") + accessibility keywords (blind, low
  vision, TalkBack, ALS, etc.)
- Accessibility-services declaration: core purpose, App functionality,
  Motor + Vision + Cognitive disabilities, target users, demo video
  URL (`https://youtube.com/shorts/v2uduk1sBkU`)

---

## Live state (as of recovery on 2026-05-18)

- ✅ **v1.5.0 APPROVED + LIVE** in Closed Testing on Play Store (May 16)
- ✅ **v1.5.1 AAB staged** on Desktop, ready to upload (10/1.5.1, 12.8 MB)
- ✅ **TC dashboard**: 25/25 testers active, Day 6+ of 16
- ✅ **Google 14-day clock**: 12 testers continuously opted in for 9 days
- ⏳ **Production unlock**: ~May 23 (5 more days)
- ✅ **Backend**: `cloudbot-ai.com/api/handy-key-setup/*` live
- ✅ **Landing**: gethandyai.app + /setup.html (Railway-deployed)

---

## Risks & considerations

1. **TalkBack co-existence (CRITICAL)**: Handy AI uses
   `AccessibilityService` for screen reading + gesture dispatch.
   TalkBack also uses `AccessibilityService`. Multiple a11y services
   can run simultaneously but testing on real hardware is required.
   **Manual test still pending — must verify before disability-org
   outreach.**

2. **API key friction**: paired-laptop flow solves the worst part for
   blind users. Long-term, managed-key free tier funded by grants
   (Microsoft AI for Accessibility, Be My Eyes Foundation) is the
   right move.

3. **Disability-washing risk**: if we add accessibility messaging but
   the app isn't actually accessible, r/Blind eats us. Build before
   market. v1.5.0 ships real features, not just marketing.

---

## Distribution channels (post-production)

**Tier 1 (start here)**
1. Blind Android Users + Accessible Android (podcast + site) —
   https://www.blindandroidusers.com/
2. National Federation of the Blind BUILD program — https://nfb.org/
3. Double Tap + Mosen At Large / Living Blindfully podcasts +
   AppleVis Android forum

**Tier 2**
- RNIB "Helpful apps" page (UK)
- Be My Eyes Foundation partnership / Microsoft AI for Accessibility grant

**Hard line: never run ads.** The blind community will eat you alive.
