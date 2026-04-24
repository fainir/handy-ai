# Handy AI — beta launch posts

Use these to recruit the 12+ Closed Testing testers Google requires. I've
tried to write each one in the voice of that platform — short + honest on
HN, a little punchier on Twitter, specific-with-examples on Reddit.

**You publish these yourself.** Customize before posting; these are
starting points, not finals.

---

## 1. Twitter / X thread (3 posts)

**Post 1**
> I built an AI agent that actually uses my Android phone for me.
>
> Type (or say) a task. It takes screenshots, figures out the next tap,
> and keeps going until it's done.
>
> Handy AI beta is open. One email → install instructions:
> https://gethandyai.app

**Post 2 (reply)**
> It runs locally — no screen recording, no proxy. Each screenshot goes
> to Claude and nothing is stored.
>
> Works on anything Android 11+. Typical task is a few cents of API.

**Post 3 (reply)**
> Examples from today's build:
> • "DM Sarah on WhatsApp I'm running late"
> • "Order my usual from DoorDash"
> • "Turn on do-not-disturb until 7pm"
>
> Anything it doesn't know? It asks. Big stop button cancels instantly.

**Media:** Attach `playstore/video/handyai-disclosure-final.mp4` (the 55s
screen recording) or 1–2 shots from `playstore/screens/`.

---

## 2. Hacker News — Show HN

**Title** (keep under 80 chars)
> Show HN: Handy AI – a Claude-powered agent that drives your Android phone

**Body** (first comment or post body)
```
I wanted a phone assistant that could do things — open Instagram and
like a specific friend's latest post, reply to a WhatsApp message,
order dinner — not just answer questions. So I built one.

Handy AI is an Android app. You type or speak a task. It takes a
screenshot, asks Claude what to tap, executes it via the Accessibility
API, takes another screenshot, and repeats until it's done. That's it.

Stack: Kotlin, Claude Sonnet via Anthropic API, Android Accessibility
Service for the screen reads + synthetic taps. No server between the
phone and Anthropic — your key, your data, your bill.

It's currently in Closed Testing on Google Play (should widen soon) and
I'm looking for ~50 beta testers to install the signed APK and actually
use it. Known rough edges:

- ~1.5-3s per step (screenshot → Claude → tap cycle)
- Dumb at very-visually-dense apps (e.g. Excel)
- No keychain: if a site asks for 2FA it just stops

What I'd love feedback on: which apps you tried, where it gave up,
where it did something you didn't expect.

https://gethandyai.app

Source: https://github.com/fainir/handy-ai
```

---

## 3. Reddit — r/androidapps

**Title**
> [Dev] Handy AI – Claude-powered agent that drives your phone for you (beta, looking for testers)

**Body**
```
Hey r/androidapps,

I'm looking for 30-50 beta testers for an Android app I've been
building. Handy AI is an agent you give a natural-language task to,
and it uses the Accessibility API to actually tap/type/swipe through
your apps until the task is done.

What that looks like:
- "Message Sarah on WhatsApp that I'm running late"
- "Order my usual from DoorDash"
- "Turn on do-not-disturb until 7pm"

You give it your Anthropic API key (stored encrypted, on-device only —
the app never sends anything to a server of mine). Typical task is a
couple of cents of Claude calls. Big Stop button kills it instantly.

It's in Google Play Closed Testing right now. Signup →
https://gethandyai.app — drop your email and I'll send install
instructions + Play Store opt-in as soon as your email is on the list.
The APK works immediately from the signup email if you don't want to
wait for Play.

Honest caveats:
- Accessibility permission is powerful. The app asks for it because
  that's how it taps on your behalf. Revokable any time.
- Model does what models do — it will sometimes open the wrong app.
  That's the bug report gold I'm looking for.
- Android 11+.

Source: https://github.com/fainir/handy-ai
```

---

## 4. Reddit — r/alphaandbetausers

Same subject, shorter:
```
Handy AI — AI agent that uses your Android phone

Android 11+, beta for Claude-powered phone agent. Give it a task in
natural language, it taps/types/swipes through your apps until done.
Looking for 30-50 testers to surface real-world failure cases.

Signup (sends install instructions): https://gethandyai.app
Source: https://github.com/fainir/handy-ai
Apk: gethandyai.app/HandyAI.apk (signed, same bundle under Play review)

Typical bug report I want: "I asked it to X, it did Y instead. Here's
the chat log." You reply to the welcome email.
```

---

## 5. Reddit — r/SideProject

```
I built a Claude-powered agent that drives my Android phone

After watching too many demos of phone agents that didn't ship, I
built one and shipped it. It's on Google Play Closed Testing; beta
signup at https://gethandyai.app.

Short version: natural-language task in, it takes a screenshot, asks
Claude what to tap, taps it, repeats until done. Runs on anything
Android 11+. Your Anthropic key, your data — no server of mine in the
loop.

Been using it for real things all month:
- morning: "turn on DND till 7pm, mute work slack"
- evening: "check if my Amazon order has shipped, show me the tracking"
- random: "reply 'on my way' to the last WhatsApp thread"

Would love honest feedback from testers — especially apps where it
failed badly.

Source: https://github.com/fainir/handy-ai
```

---

## 6. Indie Hackers — Milestone

**Title**
> Shipped: Claude-powered Android agent, now in Google Play Closed Testing

**Body**
```
Six weeks ago I started building a phone agent. Today Google approved
the Closed Testing release.

What it is: Handy AI. You tell your phone to do something in plain
English; it taps through your apps until it's done. It's an Android
app that uses the Accessibility API to read the screen and drive the
UI, with Claude Sonnet behind the wheel.

Stack: Kotlin, Claude Sonnet API, no backend server — the app talks
to Anthropic directly with the user's own key. Signed APK + Play
Closed Testing are both live. Next milestone: 12+ testers for 14 days
to unlock Play Production listing (Google's rule for new accounts).

If you'd like to poke at it: https://gethandyai.app

What I learned so far:
- The Accessibility API is shockingly capable but the Play Console
  approval bar for it is high. Getting "prominent disclosure" right
  took more iterations than the app itself.
- Running the agent 1 step at a time (screenshot → decide → act) is
  slow (~2s/step) but recoverable. Batching steps gets faster but
  blind.
- Most "hard" cases on a phone are actually easy (button positions
  are stable). The hard part is knowing when to stop.

Open-source: https://github.com/fainir/handy-ai
```

---

## What I'd do in order

1. Post the **Show HN** first (Mon/Tue morning US ET is best). It's
   usually your biggest burst of signups.
2. **r/SideProject** and **r/androidapps** within the next hour or two.
3. **Twitter thread** as it starts trending on HN — point to the HN
   post to help it climb.
4. **r/alphaandbetausers** the next day (that sub is evergreen, not
   time-sensitive).
5. **Indie Hackers** when you have a progress update to share.

Don't post everywhere simultaneously — one signup spike is easier to
respond to than five.
