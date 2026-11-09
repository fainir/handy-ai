# Handy AI 1.5.7 Play submission

## What's new

Try included Claude for up to 10 model steps. A task can take several steps. Add your own Anthropic API key at any time, or continue with it after the trial. The app explains screen sharing before enabling the included option. This update targets Android 16 and still runs on Android 11 and later.

## Store listing replacement

Handy AI helps people with vision or motor impairments control their Android phone using voice or text. Describe a task and Handy AI reads the screen, narrates its actions, and taps, types, or swipes on your behalf. Easy Mode provides a large microphone button. A Stop control cancels a running task. Review important actions and results.

The app is free to install. On eligible builds, try included Claude for up to 10 model steps per Android profile. One task can take several steps. You can add your own Anthropic API key at any time and use it when the trial ends. Anthropic bills you directly for usage with your key.

The app needs Android 11 or later and Accessibility permission to carry out tasks. When you use your own key, the phone sends the task and screen context directly to Anthropic. If you opt in to included Claude, the Betterloop gateway relays the current screenshot, task, and recent actions to Anthropic. You can turn off the trial in Settings. See the [privacy policy](https://gethandyai.app/privacy.html) for data handling and [support](mailto:hi@gethandyai.app) for help.

## Review details

The accessibility service still runs on user request and supports people with vision and motor disabilities. Included mode adds one recipient in the network path: the Betterloop gateway forwards the current screen image, task and recent actions to Anthropic. The gateway does not retain request content. It retains pseudonymous request and usage metadata for abuse and cost limits, and encrypts the response for short-term recovery. The existing personal-key path sends model requests directly to Anthropic. Review Play's Data safety and Accessibility declarations against this behavior before submitting the signed bundle to internal testing.

- In [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), account for off-device screenshots and task/action text, pseudonymous device and network identifiers, request and usage records, and encrypted responses that can be recovered for one hour. The identifiers and usage records are retained for fraud prevention and quota enforcement. The recovery response is persisted, so do not describe all processing as ephemeral. The feature is optional, and requests use HTTPS. Assess the separate pairing and configured crash-reporting behavior along with this change before signing the declaration.
- The [Accessibility declaration](https://support.google.com/googleplay/android-developer/answer/10964491) should explain the core use for users with vision or motor impairments, what the service does during user-started tasks, and what changes with the optional trial. Use actual device footage of consent, one benign task and Stop for any required demonstration.
- Use a low-risk task such as reading a synthetic screen in the internal build. Verify consent, Stop, exhaustion, Settings and the personal-key path before production rollout.
