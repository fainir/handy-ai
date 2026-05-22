# ACB (American Council of the Blind) — Braille Forum pitch

ACB publishes *The Braille Forum* (monthly) and runs ACB Media
(streaming + podcasts). They are typically more receptive to
beta-tester pitches than NFB but have a smaller distribution.

---

## Contact

**Primary:** `info@acb.org`
**Direct (Braille Forum editor):** `editor@acb.org`
**ACB Media (if pitching a podcast appearance):** `acbmedia@acb.org`
**Subject line:** Beta tester request — voice-driven Android phone agent (v1.5 a11y release)

---

## Email body

```
Hello,

I am writing to ask whether The Braille Forum or ACB Media would
consider running a short beta-tester recruitment notice for an
Android accessibility app, or whether there is a more appropriate
contact for that kind of request.

The app is Handy AI. Voice in, voice out: you tell the phone what
you want done, and it operates your existing apps for you via the
Android Accessibility Service. It is on Google Play Closed Testing
as of 2026-05-18. Free, open source, BYO Anthropic API key (a few
cents per task is typical).

What is specific to this v1.5 release and would be of interest to
ACB members:

  * TalkBack-aware. The agent detects when a screen reader is
    active and avoids speaking over it.
  * Voice narration of every step the agent takes, at three
    verbosity levels.
  * Voice-confirm before destructive actions ("about to delete
    this — say cancel if not"), with a 1.5 second grace window.
  * On-device OCR for "read me the screen" — uses Google ML Kit
    locally, no Anthropic tokens consumed, no data leaves the
    phone.
  * Named aliases — say "Mom" once with the real contact name, and
    it remembers.
  * Easy Mode — one full-screen microphone button, no menus.
  * Paired-laptop API key setup — type the API key once on a
    laptop, code-pair the phone. Avoids typing "sk-ant-..." on a
    phone keyboard.
  * HighContrast theme at WCAG AAA contrast.
  * Declared in the Play Console as an accessibility tool, not a
    productivity app.

What we are honest about NOT shipping yet:

  * Native braille display support. We currently rely on TalkBack
    to relay; a Brailliant or Focus user would get the TalkBack
    rendering, not a tuned surface.
  * Switch control or eye gaze input optimization.
  * First-run install needs sighted help (Play install button or
    APK side-load).

What I am asking: a brief notice in an upcoming Braille Forum, or a
short interview slot on ACB Media if that is appropriate. Drop-in
copy below.

---

Short notice for The Braille Forum:

  Beta testers wanted for Handy AI, a voice-driven Android agent
  with TalkBack-aware narration, on-device screen reading via OCR,
  voice-confirm before destructive actions, and a one-button Easy
  Mode. Free, BYO Anthropic API key, currently in Google Play
  Closed Testing. Sign up at gethandyai.app to receive install
  instructions and the Play Store opt-in link.

---

Longer notice (if there is room for a half-column):

  Handy AI is an Android app that operates your existing apps for
  you on voice commands. Tell it "text Lisa I am running ten
  minutes late" or "read me this notification" or "order my usual
  from DoorDash", and it takes a screenshot of the screen, sends
  it to Anthropic's Claude model to decide the next tap, executes
  the tap via the Android Accessibility Service, and repeats until
  the task is done. Every step is narrated. Destructive actions
  are voice-confirmed with a grace window. A laptop-side API key
  setup avoids ever needing to type the API key on a phone
  keyboard. The OCR for "read me this screen" runs on-device with
  no API cost.

  The 1.5 release shipped 2026-05-18 with the accessibility
  features above as the primary product positioning. The app is
  free; users pay Anthropic directly for API usage (typically a
  few US cents per task). Source is open on GitHub. We are not
  claiming this replaces TalkBack, Voice Access, Be My AI, or
  Seeing AI — those each do specific things better. We are
  building the layer that lets a person who would rather speak
  than touch their phone get tasks done across any installed
  app.

  Sign up at gethandyai.app to receive install instructions and
  the Google Play Closed Testing opt-in link. Source:
  github.com/fainir/handy-ai. Feedback is gold — beta testers
  will be asked to reply to one short email at the end of two
  weeks.

---

Demo video: gethandyai.app/#voices (sixty-three seconds, captioned,
audio described).

Thank you for the time. Happy to provide an accessible PDF,
schedule an ACB Media call, or take this to another contact you
suggest.

Sincerely,
Nir Fainshtein
fainir2006@gmail.com
```

---

## Notes for the user before sending

1. ACB Media specifically may want a phone or web call. If so,
   schedule it — it is typically 15-20 minutes and reaches a much
   bigger audience than a print notice.
2. Do not promise braille display support in any reply. If they
   ask, the honest answer is "TalkBack relays it today; a native
   braille surface is on the roadmap but not committed." That
   answer reads as adult; promising and missing reads as the
   opposite.
3. Save any commitment-to-publish dates. The Play production-
   unlock clock cares about a fourteen-day window; aligning a
   newsletter mention with that window doubles its value.
