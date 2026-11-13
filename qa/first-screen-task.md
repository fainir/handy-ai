# Handy AI first screen-reading task

Google Play reported 293 device acquisitions and 97 device first opens in its latest 28-day window. First opens were down 10%. These counts measure different device groups. This Android change helps people who open Handy AI and reach setup. It gives them the same screen-reading example already explained on the website. [First-task guide](https://gethandyai.app/#install) · [Play metric definitions](https://support.google.com/googleplay/android-developer/answer/139628/view-app-statistics-computer).

## Android acceptance

Built the isolated debug package on JDK 17 and SDK 36. Android 16 ran as a fresh emulator with package `com.claudeagent.phone.qa`. Phone touch checks used 375dp. A wider 720dp run used 130% text and keyboard focus. All screens contained synthetic text. [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).

| Journey | Observed result |
|---|---|
| First launch and trial consent | The example and Start were unavailable before setup. Declining the existing screen-sharing dialog kept the composer disabled. Agreeing enabled it and showed the Accessibility shortcut. |
| Accessibility setup with a typed draft | The shortcut opened Android Accessibility settings while a draft was present. Enabled the test service via ADB on the isolated emulator and verified it was bound. Back returned to the same draft with the example disabled. |
| Cancelled task and new chat | Started a typed request with Accessibility off, dismissed the permission dialog with system Back and opened a new chat. The setup shortcut and return left the new chat empty, with no task running. |
| First example by touch and keyboard | The 48dp phone button filled and focused “Read the text on this screen.” The chat remained empty. Tab and Enter produced the same editable draft on the wider screen. |
| Existing Start and voice controls | Start alone created one task. An offline check showed the existing API error and returned the composer to idle. The microphone stayed enabled. This emulator lacks a speech recognition handler, and tapping the mic preserved the draft. |

The [setup screen](first-screen-task-setup.png), [phone draft](first-screen-task-375.png) and [keyboard draft](first-screen-task-720.png) show the tested flow. All 38 JVM tests passed. The new controls reuse the existing Claude choice, consent dialog and Start action. The latest included-Claude screen-reading task that reached the live model was tested on the prior signed 1.5.7 build. [Prior release proof](https://github.com/fainir/handy-ai/blob/main/qa/claude-trial.md).

## Release and use

For the next Play build, verify the same route in the signed Android package. Read an outside user’s report of a successful screen read and a return within seven days. Use Play’s first-open and seven-day return counts as device-level context. The prefill does not add an analytics event or count as a completed task.
