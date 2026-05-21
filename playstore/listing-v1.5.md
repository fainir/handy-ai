# Play Store Listing — Handy AI v1.5.x (Accessibility Pivot)

Copy-paste fields for Play Console → Store presence → Main store listing.

---

## App name (max 30 chars)
```
Handy AI
```

## Short description (max 80 chars)
```
Voice-first AI agent for any Android app. Built with accessibility in mind.
```

## Full description (max 4000 chars)
```
Handy AI is a voice-first AI assistant that uses your Android phone for you.

Speak a task in plain English — "Message Sarah on WhatsApp that I'm running late", "Order my usual from DoorDash", "Read me the menu on screen" — and Handy AI takes a screenshot, asks Claude what to tap next, executes the tap, and repeats until the task is done. It narrates each step out loud, voice-warns before destructive actions, and confirms when it's done.

FOR EVERYONE

- Hands-free when you're driving or your hands are full
- Multi-step tasks in one command instead of a dozen taps
- Works in any installed app -- no partner allowlist
- Big Stop button kills any task instantly
- Your data stays on your device -- no Handy AI server between your phone and Anthropic

BUILT FOR ACCESSIBILITY

Designed alongside the needs of blind, low-vision, motor-impaired, and cognitive-accessibility users.

- Verified TalkBack compatibility -- every button labelled, focus order correct
- Text-to-speech reads every assistant reply aloud (auto-on when TalkBack is detected)
- Audible action narration -- "Tapping Send. Now in WhatsApp. Message sent."
- Voice-warn before destructive actions (Send, Buy, Delete) so you can hit Stop if the agent got it wrong
- Easy Mode: one giant mic button, no chat history, no menus
- High-contrast text option (pure white on pure black, WCAG AAA)
- Haptic feedback for task started, done, error, and confirmation prompts
- Set-up-by-computer: get a 6-character code on your phone, enter it on your laptop at gethandyai.app/setup, paste your Anthropic key with your desktop screen reader -- phone receives it
- Audit-log voice command: ask "what did you do?" and Handy AI reads back the last few actions
- Bluetooth headset + hearing aid compatible

Handy AI is the only AI agent on Android that combines open-ended automation with verified accessibility-tool status. Other apps (Be My AI, Seeing AI, Lookout, Envision) describe your screen -- Handy AI executes the task on it.

HOW IT WORKS

1. Tap the mic, speak a task. Or type it.
2. Handy AI screenshots your screen.
3. Sends the screenshot to Claude with your task.
4. Claude returns the next action (tap, type, swipe, etc.).
5. Handy AI executes the action via the Accessibility API.
6. Repeat from step 2 until done.

REQUIREMENTS

- Android 11 or higher
- Anthropic API key (free $5 credit at console.anthropic.com)
- Accessibility permission (granted once, revoke anytime)

PRIVACY

Your Anthropic key lives encrypted on your phone only. Screenshots go directly from your phone to Anthropic's Claude API -- never to a Handy AI server. We store nothing, train on nothing, proxy nothing.

KEYWORDS

blind, low vision, screen reader, TalkBack, voice control, hands free, motor accessibility, ALS, Parkinson's, vision impaired, voice command, voice assistant, accessibility AI, AI agent, Claude, Anthropic, phone automation, Easy Mode, multi-step tasks, AI accessibility

SOURCE & SUPPORT

Open source: github.com/fainir/handy-ai
Email: fainir2006@gmail.com
Privacy: gethandyai.app/privacy.html
```

---

## Accessibility-services declaration

### Core purpose
```
Handy AI is a voice-first assistant for users with vision impairment or motor disabilities. The user speaks a task ("Send Sarah a message", "Order DoorDash"); Handy AI reads the screen via Accessibility API, narrates each step aloud, and performs the taps and swipes the user cannot make. For blind users it operates apps that lack proper TalkBack labels; for motor-impaired users (ALS, Parkinson's) it replaces precise touch input with voice.
```

### Usage
Check **App functionality**.

### Disabilities
Check **Motor**, **Vision**, **Cognitive or learning**. Leave Hearing and Other unchecked.

### Target users
```
Blind and low-vision users (who can't use small touch targets or read inaccessible app UIs), motor-impaired users (ALS, Parkinson's, hand tremor, paralysis -- who can speak but cannot perform precise touch input), older users and those with mild cognitive decline who get overwhelmed by app-by-app navigation but can express intent in plain English. Also benefits anyone who needs hands-free phone control (driving, kids, cooking).
```

### Video instructions
```
https://youtube.com/shorts/v2uduk1sBkU
```

---

## v1.5.0 status

- Sent for review 2026-05-16
- **APPROVED + PUBLISHED 2026-05-16 8:13 PM**
- Live in Closed Testing track (versionCode 9, versionName 1.5.0)
- 25/25 TestersCommunity testers active
- 12 testers opted-in continuously since at least 2026-05-08

## v1.5.1 status

- AAB built at `~/Desktop/HandyAI-v1.5.1.aab` (12.8 MB, versionCode 10, versionName 1.5.1)
- New features: OCR via MlKit, named aliases, disambiguation rule, common-task library, aliases-editing Settings UI
- **Pending upload** to Closed Testing track
- Release notes:
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
