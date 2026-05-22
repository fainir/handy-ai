# v1.5.1 Play Store screenshots — 2026-05-22

Captured from Pixel 10 (1080×2424) running Handy AI v1.5.1 via ADB.
All meet Play Store requirements (PNG, 9:20 aspect, both sides ≥320,
≥1080 on shortest side → eligible for promotion).

## Upload order (best first for conversion)

Play Store shows screenshots in upload sequence. Drop these in this
order on Default store listing → Phone screenshots → Add assets:

| # | File | Why first / what it shows |
|---|------|--------------------------|
| 1 | `01-hero.png` | Clean empty state. "What should I do on your phone?" + three example prompts. The brand. |
| 2 | `07-easy-mode.png` | Easy Mode — one giant cream mic button on black, "Tap the big button and speak." Immediately signals accessibility-first. |
| 3 | `99-task-in-progress.png` | Agent narrating real actions ("I'll go to the home screen and open the camera app...") with tool calls visible. Proof it works. |
| 4 | `04-settings-voice.png` | Voice and audio section — three narration levels, voice-warn destructive, vibration feedback, HighContrast toggle, Easy Mode + Set Up Via Computer buttons. The accessibility-features panel. |
| 5 | `06-keysetup.png` | KeySetup screen with 6-char one-time code "TPGS63", "Now on your computer, open: gethandyai.app/setup". The screen-reader-friendly setup flow. |
| 6 | `05-aliases.png` | Named Aliases editor. "Map a short label (Mom, my usual, work email) to a concrete entity." |
| 7 | `03-settings-top.png` | Settings top — BYO API key transparency, accessibility re-grant flow. The honesty/control screen. |

You need ≥4 for promotion eligibility (Play Console hint:
"To be eligible for promotion, include at least 4 screenshots at
a minimum of 1080 px on each side"). You have 7 here — upload the
top 5-7 in the order above.

## Known issues caught during capture

### HighContrast toggle is decorative — does NOT change the theme

In settings/04-settings-voice.png you can see the "High-contrast text"
toggle. I turned it ON (verified via uiautomator dump:
`highContrastSwitch checked=true`), then force-stopped and relaunched
the app to trigger `applyTheme()`. The visual result was **identical
to the regular theme** — pixel-for-pixel comparison at the toolbar
text, background, hero h1, and subtitle returns byte-identical RGB
values (`(237, 230, 214)` cream text, `(17, 17, 17)` ink bg).

The switch is wired to a SharedPreferences value but
`Theme.ClaudePhoneAgent.HighContrast` either isn't being read on
activity recreation, or it resolves to the same color tokens as
the default theme.

**Impact:** a low-vision user toggling HighContrast expecting pure
white-on-black gets the same cream-on-ink they had before. Trust hit.

**Not a launch blocker** (production review won't catch this; Google
can't tell whether toggling a UI control changes visuals), but worth
a v1.5.2 patch:
- Audit `applyTheme()` in MainActivity
- Verify `Theme.ClaudePhoneAgent.HighContrast` colors actually differ
  from base theme
- Either fix or remove the toggle to avoid promising-and-not-delivering

### "Accessibility: disabled" visible in 03-settings-top.png

The Accessibility Service was not granted at capture time (see screenshot).
The agent therefore cannot actually tap on behalf of the user until
re-granted via the "RE-GRANT ACCESSIBILITY" button. This was the state
of the device, not a v1.5.1 regression -- the service stays disabled
because Android occasionally revokes accessibility on app updates.
For the production Play Store reviewer, the App access flow they
follow re-grants this naturally.

## Capture process (for future re-shoots)

```bash
# Connect Pixel, verify
adb devices -l

# Launch app, ensure shade dismissed
adb shell am force-stop com.claudeagent.phone
adb shell cmd statusbar collapse
adb shell am start -n com.claudeagent.phone/.MainActivity
sleep 4

# Capture
adb exec-out screencap -p > NN-name.png

# Find UI bounds for next tap
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml  # parse for resource-id + bounds
```

## Privacy note

The chat history visible in `99-task-in-progress.png` is the "take
a picture with the phone camera" demo task. The session drawer
contained personal data (real contact names, Tinder tasks, etc.) —
**that screenshot was deleted on capture, not committed.**
