# Claude Phone Agent

Native Android app that uses Claude Opus 4.7 to control the phone on your behalf. You type a task in plain English, the app screenshots the screen, sends it to Claude, receives a tool call (tap / swipe / type / key), executes it via Android's AccessibilityService, and loops until done.

This is a sideload build. It is **not** safe for the Play Store: general-purpose automation via AccessibilityService violates Google's policy.

## How it works

1. `AgentAccessibilityService` uses the Android 11+ `AccessibilityService.takeScreenshot()` API to capture the screen and `dispatchGesture()` to inject taps and swipes.
2. `AnthropicClient` calls the Messages API at `https://api.anthropic.com/v1/messages` with `model = claude-opus-4-7`, vision input, and a set of phone-control tools.
3. `AgentLoop` drives the loop: screenshot → Claude → tool call → execute → new screenshot → Claude → ... until the model calls the `finish` tool or the step limit is hit.

## Build

You need Android Studio (Hedgehog or newer) and an Android device on Android 11+ (API 30).

1. Open `~/Documents/claude-phone-agent` in Android Studio. It will download Gradle and sync the project (this generates `gradle/wrapper/gradle-wrapper.jar` automatically).
2. Plug in an Android phone with USB debugging enabled.
3. Run the `app` configuration. The APK installs on the device.

If you prefer CLI:
```bash
cd ~/Documents/claude-phone-agent
# One-time, needs a system Gradle installed:
gradle wrapper --gradle-version 8.9
./gradlew :app:installDebug
```

## First run on the device

1. Open the **Claude Phone Agent** app.
2. Paste your Anthropic API key (starts with `sk-ant-`) and tap **Save key**. It is stored in `EncryptedSharedPreferences`.
3. Tap **Grant Accessibility permission**. In Settings → Accessibility → Installed apps, enable **Claude Phone Agent**. Return to the app - the status will switch to "Granted".
4. Type a task, for example: *"Open Chrome, search for weather in Tel Aviv, and summarize the forecast."*
5. Tap **Start task**. Minimize the app. Claude will drive the phone.
6. Watch the log. Tap **Stop** to cancel.

## Tools exposed to Claude

| Tool | Input | Effect |
|---|---|---|
| `tap` | `x`, `y` | Tap at screen pixel coordinates |
| `long_press` | `x`, `y`, `duration_ms?` | Hold at coordinates |
| `swipe` | `x1`, `y1`, `x2`, `y2`, `duration_ms?` | Swipe/drag |
| `type_text` | `text` | Type into the focused field |
| `clear_text` | - | Clear focused field |
| `key` | `action` ∈ {`back`, `home`, `recents`, `ime_enter`} | System key |
| `wait` | `ms` | Pause up to 3s |
| `finish` | `success`, `summary` | Stop the loop |

## Design notes

- **Model:** `claude-opus-4-7` with vision. Extended thinking is off; tool-use alone is enough.
- **Prompt caching:** the system prompt is sent with `cache_control: ephemeral` so subsequent steps hit the cache.
- **Context growth:** only the last 3 screenshots are kept in the message history; older ones are replaced with a text summary of the action result.
- **Min Android:** API 30 (required for `takeScreenshot()` and `ACTION_IME_ENTER`).
- **Screen coordinates:** pixels from top-left of the current window bounds, sent to Claude in the system prompt.

## Limits you will hit

- `takeScreenshot()` is rate-limited to roughly once per second. The loop includes a 700 ms settle delay after every action, which is usually enough.
- Typing into web form fields sometimes fails because they do not expose `ACTION_SET_TEXT`. Tap → wait → try again, or have Claude fall back to tapping on-screen letters.
- The AccessibilityService may be killed by the system if the phone is low on memory. Keep the device plugged in for long tasks.
- Cost: each step sends a screenshot. A 20-step task with 1280-px JPEGs at quality 70 is roughly 1-3 MB of image tokens. Budget accordingly.

## Security

- API key is stored via `EncryptedSharedPreferences` (AES-256-GCM, master key in the Android Keystore).
- The app has no exported components except the launcher activity.
- The system prompt explicitly forbids Claude from entering passwords, payment info, or running destructive actions (delete, uninstall, purchase, send money) unless you asked for that specific thing. This is a guideline to the model, not a hard sandbox - treat it accordingly.

## Files

```
app/src/main/
├── AndroidManifest.xml
├── java/com/claudeagent/phone/
│   ├── AgentAccessibilityService.kt   # screenshots + gestures + owns the agent scope
│   ├── AgentLoop.kt                   # orchestrates screenshot → Claude → action
│   ├── AgentState.kt                  # shared state flows (status, log, run state)
│   ├── AgentTools.kt                  # JSON tool definitions sent to Claude
│   ├── AnthropicClient.kt             # OkHttp-based Messages API client
│   ├── ApiKeyStore.kt                 # EncryptedSharedPreferences wrapper
│   └── MainActivity.kt                # UI
└── res/xml/accessibility_service_config.xml
```
