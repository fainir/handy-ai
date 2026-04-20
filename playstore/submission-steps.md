# Play Store submission — final 30 minutes

Everything below has to happen in a browser signed in to a Google account.
I can't do any of it for you without your login. All the heavy lifting is done.

## What's already produced for you

| File | What it is |
|---|---|
| `app/build/outputs/bundle/release/app-release.aab` | Signed Android App Bundle. Upload this to Play Console. |
| `app/handy-ai-release.jks` | Signing key. **Back this up somewhere safe (1Password, a private drive, wherever).** Losing it means you can never ship an update. |
| `keystore.properties` | The passwords. Same story — back up, don't lose. |
| `playstore/app-icon-512.png` | 512×512 app icon. |
| `playstore/feature-1024x500.png` | Feature graphic for the listing header. |
| `playstore/listing.md` | Title, short description, full description, categories, Data Safety answers. Copy-paste into Play Console. |
| `playstore/privacy-policy.md` | Privacy policy text. You need to host this at a public URL before you submit. |
| `playstore/terms.md` | Terms of service text. |

## Steps

1. **Pay the $25 Play Console fee** at https://play.google.com/console/signup — one-time, any Google account.

2. **Host the privacy policy** — copy `playstore/privacy-policy.md` as an HTML page on any static host (GitHub Pages, Netlify, your own site) and note the URL. Play Console will not accept a submission without a public, reachable privacy-policy URL. Fill in the "[your support email]" placeholder first.

3. **Create the app** in Play Console → All apps → Create app:
   - App name: **Handy AI**
   - Default language: English (United States)
   - App / game: **App**
   - Free / paid: **Free**
   - Declarations: agree

4. **Upload the bundle** — App bundles → Upload new bundle → drop `app/build/outputs/bundle/release/app-release.aab`. Play Console will show the app's version, permissions, and a first-upload wizard.

5. **Fill in the listing** using `playstore/listing.md`:
   - Main store listing → paste app name, short description, full description.
   - Upload `playstore/app-icon-512.png` as the app icon.
   - Upload `playstore/feature-1024x500.png` as the feature graphic.
   - Upload 2–8 phone screenshots (see "Screenshots" below).
   - Category: Productivity.

6. **Content rating questionnaire** — Main store listing → Content ratings → start a new rating. Answer: no violence, no sex, no gambling, no user-generated content. You'll get "Everyone".

7. **Target audience** — select 18+.

8. **Data safety** — copy answers from `playstore/listing.md` section "Data Safety form". Key points:
   - API key: stored on device, encrypted.
   - Screen content: sent to third parties (Anthropic) for app functionality, not stored.
   - Financial info: no (handled by Lemon Squeezy, we never touch card data).

9. **App access** — declare that the app needs an Anthropic API key or a subscription to be fully testable. Provide reviewer credentials:
   - Option A (simplest): a paid test subscription license key pre-activated, shipped to reviewer instructions.
   - Option B: your own `sk-ant-...` test key and instruction "paste in Settings". Don't commit that key.

10. **Accessibility service justification** — Play Console will specifically ask. Paste:
    ```
    Handy AI uses AccessibilityService to let the user hand off common on-device tasks
    described in plain language. The service reads the current screen via
    AccessibilityService#takeScreenshot() and dispatches taps/swipes/text via
    dispatchGesture()/ACTION_SET_TEXT only while the user has explicitly started a task
    from the app, and only in service of that user-described task. The user can stop
    the task at any time or disable the service from Android Settings.
    ```
    If the reviewer still rejects for "general-purpose automation", consider:
    - Narrowing the marketing copy to specific accessibility-assist use cases.
    - Starting on Internal Testing (up to 100 testers) instead of Production.

11. **Release → Production → New release** — select the uploaded bundle, paste release notes:
    ```
    First release. Describe a task by voice or text, and Handy AI carries it out for you.
    ```
    Save → Review release → Start rollout to Production.

12. **Wait** — first review is usually 1–7 days. Possible outcomes:
    - **Approved** → live within 24h.
    - **Rejected for AccessibilityService** → do not dispute; switch to Internal Testing track. Or sideload via website per `playstore/fallback-sideload.md`.

## Screenshots

Take them on your Pixel 10:

```bash
# With the phone plugged in + USB debugging:
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/platform-tools:$PATH
mkdir -p playstore/screens

# 1. Onboarding screen
adb shell am start -n com.claudeagent.phone/.OnboardingActivity
sleep 2
adb exec-out screencap -p > playstore/screens/01-onboarding.png

# 2. Main empty state
adb shell am start -n com.claudeagent.phone/.MainActivity
sleep 2
adb exec-out screencap -p > playstore/screens/02-main.png

# 3. Task running
# (manually type a task, tap send, wait a few seconds)
adb exec-out screencap -p > playstore/screens/03-running.png

# 4. Settings
adb shell am start -n com.claudeagent.phone/.SettingsActivity
sleep 2
adb exec-out screencap -p > playstore/screens/04-settings.png
```

Upload all four to the Main store listing → Phone screenshots.

## If Play rejects: sideload fallback

You already have a signed `.aab`. Building a sideload APK is:
```bash
./gradlew :app:assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`. Host it on a static site, paired with an Obtainium-compatible GitHub Releases page if you want auto-updates. Accept Lemon Squeezy for subscriptions (not allowed inside a Play Store app, fully fine outside).
