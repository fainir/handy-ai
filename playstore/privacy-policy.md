# Handy AI — Privacy Policy

_Last updated: 2026-04-20_

Handy AI ("we", "the app") helps you carry out tasks on your Android phone by describing them in plain language. This page explains what data we handle and how.

## Data we collect on the device
- **Anthropic API key**, if you choose "Use my own key". Stored encrypted on your device (Android Keystore, AES-256-GCM). Never transmitted to our servers.
- **License key** and **Cloudbot device token**, if you subscribe or pair with the Cloudbot hub. Stored encrypted on your device.
- **Your task descriptions**, while a task is running. Kept in memory only.
- **A short log of recent actions** (e.g. "tapped at 540, 1200 → opened Chrome"). Kept in memory only.

## Data that leaves your device
- **Screenshots and a short action history** are sent to **Anthropic** (the company behind the Claude model) while a task is running. Anthropic processes them to decide the next action. We do not store or proxy these screenshots. See Anthropic's privacy policy at https://www.anthropic.com/legal/privacy.
- **License activation requests** are sent to **Lemon Squeezy** (the billing provider) to validate your subscription. See https://www.lemonsqueezy.com/privacy.
- **Device pairing requests** are sent to the **Cloudbot hub** (operated by us) when you choose to pair your phone with Cloudbot. The hub receives only a pairing token and coarse device metadata (model name).

## Data we do NOT collect
- We do not collect your contacts, SMS, call history, microphone, camera, or location.
- We do not build an advertising profile of you.
- We do not sell your data.

## Accessibility permission
Handy AI uses Android's Accessibility Service API strictly to:
- Take a screenshot of the current screen while a task is running.
- Dispatch taps, swipes, and text input on your behalf.

The service is only active while you have a task running. You can disable it any time in Settings → Accessibility → Handy AI.

## Payment and subscription
Subscriptions are processed by Lemon Squeezy. We do not receive or store your card number or billing details. You can manage or cancel your subscription from the email receipt Lemon Squeezy sends you.

## Data retention and deletion
- Tapping "Clear API key" in Settings removes the stored key immediately.
- Uninstalling the app removes all app data from the device.
- The Cloudbot hub keeps your device pairing record until you unpair (or request deletion via the support email below).

## Security
- On-device secrets are encrypted with the Android Keystore.
- All network calls use HTTPS (TLS 1.2+).

## Children
Handy AI is not intended for children under 13. We do not knowingly collect data from children.

## Contact
Questions, deletion requests, or complaints: **[your support email]**

_This policy may change. Material changes will be announced in-app._
