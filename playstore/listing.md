# Handy AI — Play Store Listing Content

## App name
```
Handy AI
```

## Short description (≤80 chars)
```
An AI assistant that helps you get things done on your phone.
```

## Full description (≤4000 chars)
```
Handy AI turns your phone into a hands-free helper.

Describe what you want in plain language — by voice or text — and Handy AI will help you carry it out on your phone. Send a message, look up a restaurant, find something in an old chat, or walk through a multi-step workflow you'd rather not tap through yourself.

Under the hood, Handy AI uses a vision-capable assistant (Claude by Anthropic) to look at what's on your screen, one frame at a time, and perform the taps and keystrokes needed to get you to the result.

HOW IT WORKS
1. Describe a task ("Book a table at a pizza place near the office for tomorrow at 7") by voice or typing.
2. Handy AI takes a screenshot, figures out the next step, and taps / types on your behalf.
3. You can stop any time from the app.

GREAT FOR
• Hands-busy moments — driving, cooking, walking, holding a baby.
• Anything repetitive — filling forms, following a clicky checkout flow, collecting info across apps.
• Accessibility — when typing and fine-motor gestures are hard.

TWO WAYS TO USE IT
• Start a 7-day free trial, or
• Bring your own Anthropic API key and pay per use with no subscription.

YOUR DATA
• Your API key and license key are stored encrypted on the device (Android Keystore, AES-256-GCM).
• Screenshots leave the device only when a task is running, and only to the assistant to decide the next action.
• We don't sell or share your data.
• You can revoke the assistant's screen access any time in Settings → Accessibility.

REQUIREMENTS
• Android 11+ (API 30+).
• Accessibility permission — required so Handy AI can see the screen and tap on your behalf. You enable it once, after your first task, and you can turn it off any time.

LIMITS
• Password fields and payment forms are off-limits by default — Handy AI is instructed not to enter credentials or complete purchases unless you explicitly ask.
• Some apps, especially mobile browsers with certain form fields, are harder to type into. Handy AI falls back to tapping when typing doesn't work.
• Long tasks use data. A 20-step task is usually 1-3 MB of image tokens.

FEEDBACK
Handy AI is early. If something weird happens, or an app you use doesn't work well, tell us — we read everything.
```

## Categorization
- Category: **Productivity** (primary), **Tools** (secondary)
- Tags: assistant, automation, accessibility, productivity
- Content rating: Everyone
- Target audience: 18+

## Contact details (required)
- Support email: (fill in)
- Website: (fill in, e.g. https://handy.ai or the GitHub/docs URL)
- Privacy policy URL: (must be reachable; see playstore/privacy-policy.md for the text you can host)

## Graphic assets required by Play Console

| Asset | Size | Count | Status |
|---|---|---|---|
| App icon | 512×512 PNG, 32-bit, alpha | 1 | Generate from `drawable/ic_launcher_foreground.xml` over `drawable/ic_launcher_background.xml` (108dp viewport → 512px) |
| Feature graphic | 1024×500 PNG | 1 | See `playstore/feature-graphic-spec.md` |
| Phone screenshots | 1080×1920 or 1080×2400 PNG | 2–8 | Use the device; pull with `adb exec-out screencap -p > shot.png` |

## Data Safety form (required)
- Data collected: **Anthropic API key** (purpose: app functionality; stored on device; encrypted).
- Data shared with third parties:
  - **Screen content** is sent to Anthropic (Claude) only during an active task, for the purpose of deciding the next action. Not stored by us.
  - **License key activation** call goes to Lemon Squeezy for subscribers.
- Permissions: `INTERNET`, `BIND_ACCESSIBILITY_SERVICE`.
- Data deletion: In-app clear button + uninstall wipes device storage.
- Encryption in transit: Yes (HTTPS).
- Encryption at rest: Yes (Android Keystore, AES-256-GCM).

## App-specific disclosures (Play Console will prompt)
- Foreground services: No.
- Accessibility Service use: Yes — used to perform user-requested tasks that require screen reading and input dispatching. See the intro screen in-app for full disclosure.
- Target audience does not include children.
