# Handy AI — disability-community outreach kit

Five ready-to-send templates for recruiting accessibility-community
beta testers. Written for the v1.5 accessibility-positioned build (OCR,
named aliases, voice-confirm before destructive, TalkBack-aware,
HighContrast theme, paired-laptop key setup).

**You publish these yourself.** Each file is a starting point. Read it
once, change anything that doesn't ring true, then send. If a community
detects ghostwritten copy you lose the introduction permanently.

## What's in this folder

| File | Audience | Format | Posting cost |
|------|----------|--------|--------------|
| `twitter-thread-v2.md` | General tech + accessibility crossover | 3-post Twitter thread + alt-text | Free, ~5 min |
| `r-blind-pitch.md` | r/Blind subreddit (Reddit) | Mod DM first, then post | Free, ~30 min round-trip |
| `nfb-newsletter-pitch.md` | National Federation of the Blind | Email to the comms team | Free, reply may be slow |
| `acb-braille-forum-pitch.md` | American Council of the Blind | Email to ACB Media | Free, reply may be slow |
| `rnib-connect-voices-pitch.md` | RNIB Connect (UK) | Email to RNIB Connect Voices | Free, reply may be slow |

## Order of operations (highest leverage first)

1. **Twitter thread** with `playstore/video/handyai-demo-wrapped.mp4`
   attached. Costs nothing, takes 5 minutes, lives forever in your
   pinned tweets, and drives the most traffic per minute of work.
   The MP4 has been on disk un-posted since early May.

2. **r/Blind**. Highest-density audience for the actual product. Read
   the mod-DM script in `r-blind-pitch.md` BEFORE posting — the sub has
   strict self-promotion rules and posting cold without a mod nod
   tends to get removed. The DM costs you 5 minutes and gets you a
   real introduction.

3. **NFB / ACB / RNIB newsletters**. Send all three the same day. Each
   one's editorial cycle is 2-6 weeks, so the sooner the better. Reply
   rate is realistically 1-of-3.

## Honesty bar (read this before sending anything)

The v1.5 build genuinely has these accessibility features:

- TalkBack-aware TTS narration of each agent step
- HighContrast theme (WCAG AAA pure white-on-black)
- On-device OCR via ML Kit for "read me the screen" without burning
  Anthropic tokens
- Named aliases ("Mom" → real contact) so spelling-impaired voice
  input still resolves
- Voice-confirm before destructive actions (1.5s grace period)
- Easy Mode (single giant mic button, no menus)
- Paired-laptop key setup (no typing `sk-ant-` strings on phone)
- `isAccessibilityTool="true"` declaration (Play Store regulatory
  category, not just a marketing claim)

What v1.5 does NOT yet have, and you should be upfront about:

- Braille display support (TalkBack relays, but no native braille
  surface)
- Switch-control optimization (works under switch control because
  Android does, but no tuned tap targets)
- Voice-only first-run (you still need someone to install the APK or
  open Play Store the first time)
- Eye gaze / head tracking input

Pitching what doesn't exist would be disability-washing. The honest
list above is genuinely differentiated; lean on it.

## After you post

The signup spike (if any) lands in `handy_beta_signups` on Railway
Postgres. Pull with:

```
cd ~/Documents/GitHub/cloudbot-panel
railway variables --service "Postgres-rKVX" --kv \
  | grep -E "^DATABASE_PUBLIC_URL=" \
  | sed 's/^DATABASE_PUBLIC_URL=//' > /tmp/.dbpub
/opt/homebrew/opt/libpq/bin/psql "$(cat /tmp/.dbpub)" -c \
  "SELECT created_at::date, name, email FROM handy_beta_signups
   ORDER BY created_at DESC LIMIT 20;"
rm /tmp/.dbpub
```

Production-unlock needs ≥12 distinct testers opted-in for a 14-day
window. Current count: 1 real outside tester. Gap: 11.
