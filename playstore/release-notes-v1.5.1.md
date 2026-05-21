# Handy AI v1.5.1 — Release Notes

*Paste-template for Play Console release-notes field (under 500 chars).*

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

## What changed since v1.5.0

| Feature | Notes |
|---|---|
| MlKit text recognition dep | `com.google.mlkit:text-recognition:16.0.0` |
| `read_screen_text` agent tool | OCRs whole screen locally, speaks result aloud, returns verbatim |
| `read_text_at` agent tool | OCRs a rect — for single labels / notifications |
| `NameAliases.kt` | SharedPrefs JSON map (lowercase alias → entity), injected into system prompt |
| Settings aliases UI | Add / remove rows |
| Disambiguation rule in system prompt | Agent calls `finish(success=false)` instead of guessing |
| Common-task library voice intent | Local match on "what can you do" → curated list filtered by installed apps |
| `abiFilters arm64-v8a` on release builds | Cuts direct-install APK from 63 MB to 31 MB |

## Files added in v1.5.1

```
app/src/main/java/com/claudeagent/phone/ScreenOcr.kt
app/src/main/java/com/claudeagent/phone/NameAliases.kt
```

## Files modified in v1.5.1

```
app/build.gradle.kts                         # versionCode 10, MlKit dep, abiFilters
app/src/main/java/com/claudeagent/phone/AgentTools.kt   # +read_screen_text, +read_text_at
app/src/main/java/com/claudeagent/phone/AgentLoop.kt    # OCR action handler + disambiguation prompt + NameAliases injection
app/src/main/java/com/claudeagent/phone/MainActivity.kt # isCommonTaskLibraryRequest + speakCommonTaskLibrary
app/src/main/java/com/claudeagent/phone/SettingsActivity.kt # Aliases section wiring
app/src/main/res/layout/activity_settings.xml           # Aliases edit UI
app/src/main/res/values/strings.xml                     # Aliases + common-task strings
```
