# NFB (National Federation of the Blind) — newsletter pitch

The NFB publishes *Braille Monitor* (monthly) and *Future Reflections*
(quarterly), plus a newsletter that circulates to state-chapter
mailing lists. Editorial cycle is 2-6 weeks. Best path is the
communications team — they triage.

---

## Contact

**Primary:** `communications@nfb.org`
**CC if no reply in 2 weeks:** `presoffice@nfb.org` (President's Office)
**Subject line:** Beta tester request — accessibility-first Android voice agent

---

## Email body

```
Hello,

I'm Nir, an Android developer based in Israel. I'm writing to ask
whether the NFB communications team would consider including a brief
beta-tester call in an upcoming newsletter, or pointing me to the
right contact for that.

The product is Handy AI — an Android app that takes voice or text
tasks ("text Lisa I'll be 10 minutes late", "read me this article",
"order my usual from Uber Eats") and executes them by reading the
screen and operating apps through Android's Accessibility Service.
It is in Google Play Closed Testing as of last week.

What's specific to vision accessibility (the v1.5 release shipped
2026-05-18):

  * The Accessibility Service is declared as
    android:isAccessibilityTool="true" — a Play Store policy
    category that prohibits behavioral ads and gates the listing
    behind disability-specific review.
  * TalkBack-aware narration. The app detects active screen
    readers and adjusts its own speech so it does not double-speak.
  * Step-by-step voice narration of agent actions, with three
    verbosity levels (Off / Brief / Detailed) configurable in
    Settings -> Voice and audio.
  * Voice-confirm before destructive actions. The app says
    "About to delete this email — say cancel if not" with a 1.5
    second grace window before acting.
  * On-device OCR via Google ML Kit. "Read me the screen" or
    "what does this notification say" runs locally — no API
    tokens, no data leaves the device.
  * Named aliases: a user can map "Mom" to a specific contact
    once, and the app remembers across sessions.
  * Easy Mode: single full-screen microphone button with haptic
    feedback, no menus.
  * Paired-laptop API key setup: avoids typing the API key string
    on a phone keyboard.
  * HighContrast theme variant at WCAG AAA contrast.

What it does NOT yet have, and we are not claiming:

  * Native braille display surface (the app relies on TalkBack to
    relay).
  * Switch control or eye gaze input optimization.
  * A first-run install path that does not require some sighted
    help (Play Store install button click).

The app is free. Users supply their own Anthropic API key; typical
cost is a few cents of API usage per task. We do not collect any
data on Anthropic's side — keys live on the device in encrypted
SharedPreferences.

What we are looking for: 30-50 beta testers willing to try the
v1.5 build for two weeks and reply to a single feedback email at
the end. The Google Play Closed Testing track requires twelve
testers opted-in over fourteen days before the app can be
considered for production listing — accurate feedback from
real users of accessibility tools is the bottleneck.

If a brief inclusion in a newsletter is possible, here is a one-
paragraph cut suitable for short-form newsletters:

    Beta testers wanted: Handy AI is an Android voice agent
    designed around TalkBack, on-device OCR, and voice-confirm
    before destructive actions. Free, BYO Anthropic API key,
    open source. Signup at https://gethandyai.app sends install
    instructions and a Play Store opt-in link.

Demo video (sixty-three seconds, captioned and audio described):
https://gethandyai.app/#voices
Source code: https://github.com/fainir/handy-ai

I am happy to provide a longer write-up, an interview, an
accessible PDF, or anything else that helps the team decide.
Thank you for the time.

Sincerely,
Nir Fainshtein
fainir2006@gmail.com
+972 ... (optional — include if you want phone replies)
```

---

## Notes for the user before sending

1. Replace the placeholder phone line with your actual number or
   delete it. Do not leave it as "...".
2. NFB receives many of these. The detail in the "what does NOT
   exist yet" paragraph is what makes this one credible — they
   filter for honesty.
3. If they reply with revisions, accept them. Their editorial
   judgment on framing for the blind community is better than
   yours; do not negotiate copy with them.
4. Save the thread to a labeled folder in Gmail. If they publish,
   you want the issue number and date for the launch playbook.
