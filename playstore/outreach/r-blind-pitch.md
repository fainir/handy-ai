# r/Blind pitch (mod DM first, then post)

r/Blind has explicit anti-self-promo rules. Cold-posting a new app gets
removed by automod and burns the introduction. The path that works:
DM a mod first, declare you're sighted (or low-vision — be precise),
ask permission, post with the green flair only after they say yes.

---

## Step 1 — Mod DM

**To:** any active r/Blind mod (check r/Blind sidebar → moderators →
the top one with activity in the past week)
**Subject:** "Sighted dev — is a beta-test pitch welcome?"

> Hi — I'm Nir, a sighted Android developer working on a phone agent
> that drives Android apps via the Accessibility API on voice commands.
> The v1.5 release (out in Play Closed Testing now) ships with
> TalkBack-aware narration, on-device OCR for "read me this screen",
> voice-confirm before destructive actions, and a one-giant-button
> Easy Mode.
>
> I'd like to invite r/Blind to the beta — the goal is honest feedback
> from people who actually use accessibility tools, not validation
> theater. Before I post, I want to check: (a) is this kind of post
> welcome under the current self-promo rules, (b) does anything in my
> framing need to change. I'll abide by whatever the answer is.
>
> A few specifics so you can judge: the app is free, BYO Anthropic
> key (typical cost: a few cents per task), source on GitHub, signed
> APK direct-installable from the landing page. I am sighted, I have
> not shipped any other accessibility tools before, and I'm not
> claiming this replaces anything you already use.
>
> Demo (63s, captioned + audio-described): [link to gethandyai.app]
>
> Thanks for taking the time to read this.

(Wait for the response. If yes, proceed to Step 2. If no, thank them
and don't post.)

---

## Step 2 — Post (after mod permission)

**Flair:** Use the green "Tech/App" or "Discussion" flair, whichever
the mod tells you. NEVER use "Question" — that's reserved for blind
users asking the community for help.

**Title:**
> [Beta] Handy AI — voice-driven Android agent built around TalkBack
> (sighted dev, mod-approved post)

**Body:**

```
Hi r/Blind. Mod-approved to post this.

I'm Nir, a sighted Android dev. I've been building Handy AI — an
agent that takes a voice task ("text Mom I'm running late") and
executes it by reading the screen and tapping the right things via
Android's Accessibility API. The v1.5 release is in Google Play
Closed Testing as of last week.

I'm not pitching this as a replacement for anything. TalkBack,
Voice Access, Be My AI, Seeing AI — all of these do specific things
better than I do. What I'm asking for is honest feedback from people
who actually use accessibility tools daily, on whether the v1.5
build is useful, where it falls apart, and what would have to be
true for it to earn space on your home screen.

What v1.5 actually does that's specific to accessibility:

  - TalkBack-aware: detects when a screen reader is active and
    adjusts narration so it doesn't double-speak.
  - Narrates each step aloud as it works ("Opening Messages",
    "Tapped on Mom", "Typing your message"). You can set the
    verbosity (Off / Brief / Detailed).
  - Voice-confirms anything destructive — "About to delete this
    email, say cancel if not" — with a 1.5-second grace window.
  - On-device OCR for "read me this notification" / "read me this
    article": uses Google ML Kit locally, no Anthropic tokens
    burned, no data leaves the phone.
  - Named aliases: if "Mom" doesn't auto-resolve, you can tell the
    app once "Mom = Lisa Cohen" and it remembers.
  - Easy Mode: one giant mic button covering most of the screen,
    haptic feedback on press, no menus to navigate.
  - Paired-laptop API key setup: you don't have to type "sk-ant-..."
    on the phone. Open gethandyai.app/setup on a laptop with your
    screen reader, enter the 6-character code shown on the phone,
    paste the key on the laptop.
  - HighContrast theme variant: pure white on pure black, WCAG AAA
    contrast.

What v1.5 does NOT have (real talk):

  - No native braille display support yet (relies on TalkBack to
    relay).
  - No switch-control or eye-gaze optimization. Works under them
    because Android does, but tap targets aren't tuned for them.
  - The first-run install still needs visual help (Play Store
    install button, or a sighted helper to side-load the APK).
  - Beta means it will sometimes do the wrong thing. There's a big
    Stop button.

How to try it:

  - Email signup (sends install link + Play Store opt-in):
    https://gethandyai.app
  - Direct APK (signed, same bundle that's in Play review):
    https://gethandyai.app/HandyAI.apk
  - Source: https://github.com/fainir/handy-ai

What I'd most value: bug reports with concrete repro steps — "I
asked it to X, it did Y, here's the chat log." You can reply to the
welcome email and the chat log is exportable from Settings →
Privacy → Export chat log.

Happy to answer questions here.
```

---

## Step 3 — If the thread gets traction

- Reply to every top-level comment within 24h. r/Blind has long
  memory; ghosting kills the introduction.
- If anyone reports a real accessibility failure, fix it that week
  and reply with the commit link. That's the single most credible
  thing you can do.
- Don't crosspost to r/visuallyimpaired or r/Accessibility without
  separate mod approval. Different subs, different rules.

---

## What to NOT do

- Don't use emojis in the post body. The screen reader rendering is
  inconsistent.
- Don't promise braille support, eye-gaze, or anything else not in
  v1.5. The community has heard a lot of vapor; it's the fastest
  way to lose them.
- Don't reply to skeptical comments defensively. Skeptical = engaged.
  Acknowledge, address, move on.
- Don't post the same thing to r/lowvision unless you adapt the
  copy — low-vision and totally-blind needs only partially overlap.
