# Handy AI Phone

Handy AI gives Android users a phone app and an AI assistant in one place. Choose it as the default phone app to make and receive calls on your carrier line. The keypad, contacts and recents stay available without setting up Claude. Open the assistant from Phone when you want help with a task. Start by tapping Phone in Handy AI and choosing it in Android's default app picker.

## First user and core job

Nir will use a Pixel 10 to make and receive ordinary calls on his existing SIM. Success is one outgoing and one incoming carrier call with both parties audible, the expected caller ID, and a return to the same Handy AI chat. The dialer must stay usable when AI or the Internet is unavailable.

| ID | Flow | When something is unavailable |
|---|---|---|
| F1 | Open Phone from chat and make Handy AI the default phone app | Declining keeps the existing default. Android's default app settings can switch it back. |
| F2 | Enter a number on the keypad, tap Call, and use the SIM selected by Android | An external `tel:` link fills the keypad. Invalid input stays editable. Android handles emergency routing. |
| F3 | Answer or decline incoming calls and control active calls | Notifications surface incoming calls. Hold, audio routes and keypad controls follow Telecom capabilities. |
| F4 | Find contacts, view recent calls, and call back | Permissions are optional. Android's contact picker and local call history keep working where available. |
| F5 | Ask Handy AI for help preparing a call or drafting a reply | The user types context and reviews the draft. Ordinary calling continues if Claude is unavailable. |
| F6 | Let Handy AI hear and speak on an existing SIM call | Verify an audio path on a real phone before enabling live AI, automatic screening, or transcripts. |

## Implementation

Keep the existing Kotlin app and Android 11 minimum. Android Telecom owns the phone account, call lifecycle, audio routes and emergency handoff. Contacts and recents come from Android's local providers. Call UI state stays in memory. AI reply drafting, if enabled, receives only the words a user explicitly submits and leaves the call under user control.

## Decisions

- November 13. Match the AI Phone design's keypad, recents, contacts and call surfaces inside Handy AI. Start with a default dialer and keep the assistant available from the Phone screen. [AI Phone design](https://claude.ai/design/p/15fbfebf-9fbc-415a-8f3d-1fbd85545faf) · [Phone by Google call flow](https://support.google.com/phoneapp/answer/2811745) · [Android dialer integration](https://developer.android.com/develop/connectivity/telecom/dialer-app).
- Android keeps raw carrier-call capture behind a privileged permission. Test the Pixel's Live Caption typed replies and a legitimate audio route on a real device before promising live AI. [Audio access](https://developer.android.com/media/platform/sharing-audio-input#voice-call) · [Pixel call captions](https://support.google.com/accessibility/answer/9350862#zippy=%2Ctype-responses-during-a-phone-call).
- A call release can fail if Android never binds the in-call UI, the incoming alert is hidden, or call text reaches a model without user approval. Check all three before release.
