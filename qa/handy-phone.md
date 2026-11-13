# Handy AI Phone candidate checks

## Android setup

Build the isolated APK with JDK 17 and SDK 36. The QA package can coexist with the installed Handy AI app.

```sh
./gradlew -PisolatedQa=true assembleDebug testDebugUnitTest lintDebug
```

## Device tests

1. Open Phone from chat before setting up Claude. Cancel the Android role picker and confirm the original default. Accept it and verify Handy AI is Android's default phone app. Return to chat.
2. Open a synthetic `tel:` link and confirm the keypad fills without placing the call. Tap Call and verify the outgoing status, answer, mute, audio route, DTMF and hang-up. Repeat with a busy endpoint. Use a consenting test endpoint for physical SIM checks.
3. Simulate incoming calls on the emulator with `adb emu gsm call 5550101`. Answer from the notification, then decline another call. Repeat with the screen locked and full-screen permission denied.
4. Search a synthetic contact and open it on the keypad. Check permission denial. Verify outgoing and missed or declined calls in Recents. Restore the original default app and check that Handy AI hides the call history.
5. Use Draft with AI in an active call. Exercise a missing key, blank prompt, cancel, and a valid reply. Confirm the request contains only the typed text. Check that recent calls, contact results and the in-call UI are protected from screen capture.
6. On a Pixel, confirm both sides hear the call and the other phone sees the expected caller ID. Verify incoming calls while locked and check Live Caption typed replies on a consented test call.

## November 13, 2026

- Built and verified the debug APK signature for `com.claudeagent.phone.qa`, targeting Android 16 with Android 11 minimum. Android lint reported zero errors. All 43 JVM tests passed, including number normalization, spoken and typed dial requests, and the explicit text-only AI draft request.
- Independent source review found four issues. The candidate now pins the SIM, lets Android route emergencies, uses a new task for call alerts, respects Do Not Disturb, and protects the Phone and draft windows from screenshots.
- The physical Pixel was absent from `adb devices`. The disposable emulator exceeded available SSD space, then a RAM-backed run failed during Android's first-stage mount before ADB came online. The device flows and visual checks above still need to run. No call was placed.
- Play publication needs device evidence and matching Call Log and full-screen intent declarations. [Call Log policy](https://support.google.com/googleplay/android-developer/answer/16558241) · [Full-screen intents](https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi).
