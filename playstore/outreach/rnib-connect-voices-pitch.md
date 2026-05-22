# RNIB Connect Voices (UK) — beta tester pitch

The Royal National Institute of Blind People (RNIB) runs RNIB
Connect Voices, a community magazine and podcast network. They are
the UK equivalent of NFB / ACB and tend to be receptive to
accessibility-tech outreach IF the tech is genuinely accessible-
first (not just relabelled).

Different audience to ACB / NFB — UK English, GDPR-aware, more
inclined to ask about price-in-pounds and where data goes.

---

## Contact

**Primary:** `connectvoices@rnib.org.uk`
**Backup (RNIB Tech team):** `tfl@rnib.org.uk` (Technology for Life)
**Subject line:** Beta testers wanted — accessibility-first Android voice agent (v1.5)

---

## Email body

```
Hello,

I am writing to ask whether RNIB Connect Voices would consider
running a brief beta-tester recruitment notice for an Android
accessibility app, or whether I should direct this to a different
RNIB team.

The product is Handy AI. It is an Android app that takes a voice
command ("text Lisa I am running late", "read this article",
"order my usual from Deliveroo") and operates your existing apps
for you via the Android Accessibility Service. The 1.5 release
shipped to Google Play Closed Testing on 2026-05-18.

Specific to a UK accessibility audience:

  * Free to download. Users supply their own Anthropic API key —
    typical cost is a few pence per task. No subscription, no ads,
    no in-app purchases.
  * Data flow: the screenshot the agent uses to decide the next
    tap is sent to Anthropic over TLS and is not stored. No
    server of ours sits between the phone and Anthropic. No
    telemetry beyond crash reports (Sentry, gated on user
    consent, redactable, off by default in the v1.5 build).
  * UK GDPR position: we are not a data controller. We do not
    receive user content. Users can revoke the Accessibility
    permission at any time and uninstall removes everything.

Specific to vision accessibility (v1.5):

  * TalkBack-aware. The app detects when a screen reader is on
    and adjusts its own narration so it does not double-speak.
  * Step-by-step voice narration with three verbosity levels
    (Off, Brief, Detailed). Configurable in Settings.
  * Voice-confirm before destructive actions, with a 1.5 second
    grace window.
  * On-device OCR for "read me the screen" or "what does this
    notification say". Uses Google ML Kit locally — no Anthropic
    API tokens spent, nothing leaves the phone.
  * Named aliases. Tell the app once that "Mum" is a specific
    contact and it remembers. Useful for voice input where speech
    recognition can be inconsistent with names.
  * Easy Mode — single full-screen microphone button with haptic
    feedback, no menus.
  * Paired-laptop API key setup — enter a six-character code on
    the laptop at gethandyai.app/setup, paste the API key on the
    laptop. Avoids ever typing the API key on the phone.
  * WCAG AAA contrast theme variant.

Honest about NOT yet:

  * No native braille display support. TalkBack relays.
  * No switch control or eye gaze tuning.
  * First-run install still needs sighted help for the Play
    Store install button or the APK side-load.

Drop-in copy suitable for Connect Voices print or web:

  Handy AI is a free Android app that operates your phone for you
  on voice commands. Voice in, voice out: tell it what you want
  done and it taps through your existing apps until it is. The
  1.5 release ships TalkBack-aware narration, on-device OCR for
  reading screens aloud, voice-confirm before destructive
  actions, a one-button Easy Mode, and a laptop-side setup so you
  never type an API key on the phone. Free, source-open, BYO
  Anthropic key (a few pence per task). Beta testers wanted —
  sign up at gethandyai.app.

What we are asking for: thirty to fifty UK-based beta testers
willing to try the v1.5 build for two weeks and reply to one
feedback email at the end. Google's Play Console requires twelve
testers opted in for fourteen days before the app can be
considered for production; UK users of accessibility tools would
be a high-signal cohort.

Demo (sixty-three seconds, captioned and audio described):
gethandyai.app/#voices
Source: github.com/fainir/handy-ai

I am happy to send an accessible PDF, do a phone call, or hand
this off to whichever RNIB contact is best placed to evaluate.

Thank you for the time.

Best,
Nir Fainshtein
fainir2006@gmail.com
```

---

## Notes for the user before sending

1. RNIB asks about data flow in detail. The paragraph above is
   accurate. If they ask for a Data Protection Impact Assessment,
   the honest answer is: "We do not act as a data controller. The
   only DPIA-relevant data flow is the user's screenshot to
   Anthropic over TLS. Anthropic publishes their own DPIA. Local
   storage is encrypted SharedPreferences and is purged on
   uninstall."
2. UK price expressions: pence, not cents. Verify the draft
   carries pence after any future edits.
3. If they say yes, ask whether they prefer a print mention, a
   podcast slot on RNIB Connect Radio, or both. The radio slot
   reaches a much bigger audience than the print.
4. RNIB's editorial calendar runs about a month ahead. Send this
   ASAP if you want a June inclusion.
