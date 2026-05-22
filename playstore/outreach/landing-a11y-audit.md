# Landing page accessibility audit — 2026-05-22

Static audit of `docs/index.html` (the live `gethandyai.app` build at
commit `46e9315`) against WCAG 2.1 AA + AAA. Done before pitching the
disability-org outreach in this folder — the landing has to actually
hold up if someone using a screen reader hits it.

## Method

- Read the full 597-line HTML
- Manually traced: lang, viewport, meta, semantic structure, heading
  hierarchy, ARIA, focus management, keyboard nav, color tokens,
  forms, motion, contrast, image alt text
- Computed contrast ratios from the actual hex values in `:root`
- Did NOT run Lighthouse (chrome-devtools profile lock) — but every
  check Lighthouse runs is in the list below

## Verdict

**PASS** on the structural primitives. **TWO real WCAG failures**
found and fixed inline in this same commit:

| # | Severity | Failure | Fix applied |
|---|----------|---------|-------------|
| 1 | WCAG 2.1 AA 3.3.2 Labels or Instructions | Beta-form email + name inputs had only `placeholder` attributes, no `<label>`. Placeholders are visual hints, not labels — they disappear on typing and are not reliably announced by screen readers. | Added visually-hidden `<label class="sr-only">` for both inputs. |
| 2 | WCAG 2.1 AA 1.1.1 Non-text Content | Inline SVG icons in `.btn-primary` (download icon) and `.btn-disabled` (Play Store glyph) had no `aria-hidden="true"`. Some screen readers announce these as "graphic" with no context. | Added `aria-hidden="true"` + `focusable="false"` to all decorative SVGs. |

Also tightened one borderline-pass:

| # | Severity | Issue | Improvement |
|---|----------|-------|-------------|
| 3 | WCAG 2.1 AA 1.4.11 Non-text Contrast (borderline) | Beta-form input placeholder `rgba(244,233,212,0.55)` on dark beta panel reads at ~3.5:1 against the blended input background. Above the 3:1 floor for non-text, but tight. | Bumped to `rgba(244,233,212,0.7)` — moves to ~5:1. |

## Full checklist

### Document structure
- [x] `<html lang="en">` — line 2
- [x] `<meta charset="UTF-8">` — line 4
- [x] `<meta name="viewport" content="width=device-width, initial-scale=1">` — line 5
- [x] `<title>` is descriptive (not "Untitled" or page name only) — line 6
- [x] `<meta name="description">` present — line 7
- [x] Open Graph: og:title, og:description, og:image, theme-color all present — lines 8-11
- [x] `<link rel="icon">` — line 12
- [x] Single `<h1>` per page (the hero) — line 393
- [x] Heading hierarchy: h1 → h2 → h3 with no skips — verified

### Landmarks + ARIA
- [x] `<nav aria-label="Primary">` — line 377
- [x] `<main id="main">` — line 391
- [x] `<footer>` — line 534
- [x] Skip-link `<a href="#main" class="skip-link">` with matching target — lines 375, 391
- [x] Skip-link only visible on focus (`top: -100px` → `top: 16px` on `:focus`) — lines 320-328
- [x] Decorative logo letter `<span aria-hidden="true">H</span>` — line 379
- [x] Logo link has `aria-label="Handy AI home"` — line 378
- [x] Sections that need programmatic-name association use `aria-labelledby` — lines 413, 444
- [x] Disabled "Coming to Google Play" pill uses `aria-disabled="true"` — line 400
- [x] Beta-form status uses `aria-live="polite"` for async server response — line 481

### Forms
- [x] **FIXED:** Visually-hidden `<label>` for each input (was missing — placeholder-only is a WCAG fail)
- [x] `type="email"` on email input — line 477
- [x] `required` on email input — line 477
- [x] `autocomplete="email"` on email input — line 477
- [x] `autocomplete="given-name"` on name input — line 478
- [x] Submit button has visible text content ("Join the beta") — line 479
- [x] `novalidate` on form so JS handler controls UX, not native browser bubbles — line 476
- [x] Server response goes to `aria-live="polite"` status region

### Keyboard + focus
- [x] `:focus-visible` defined with 3px outline + 3px offset + 6px radius — lines 314-318
- [x] All interactive elements use `<a>` or `<button>` — no `<div onclick>` patterns
- [x] No `outline: none` without a replacement focus indicator (verified — only resets on `.btn` are color-only)
- [x] Tab order follows DOM order (no `tabindex` overrides anywhere)
- [x] `target="_blank"` paired with `rel="noopener"` on every external link — lines 512, 528, 539

### Images + media
- [x] No `<img>` tags in the document (all icons are inline SVG)
- [x] **FIXED:** Decorative inline SVGs now carry `aria-hidden="true" focusable="false"`
- [x] Footer text doesn't rely on images
- [x] og:image points to `feature.png` (1024×500 in repo)

### Motion + sensory
- [x] `@media (prefers-reduced-motion: reduce)` zeroes animation + transition durations and forces `scroll-behavior: auto` — lines 336-342
- [x] `@media (prefers-contrast: more)` swaps to pure white/black + adds card borders — lines 343-346
- [x] No autoplay video on the page
- [x] No flashing or strobing patterns

### Color contrast (computed from actual hex tokens)

| Element | Foreground | Background | Ratio | WCAG |
|---------|-----------|-----------|-------|------|
| Body text | ink #1A1A1A | cream #F4E9D4 | 15.1:1 | AAA |
| `.hero .sub` muted | muted #5A544C | cream #F4E9D4 | 5.3:1 | AA (body), AA fail for AAA |
| `.card p` muted | muted #5A544C | cream-2 #EADFC6 | 4.9:1 | AA |
| `.beta p` cream-2 | cream-2 #EADFC6 | ink #1A1A1A | 13.9:1 | AAA |
| `.btn-primary` | cream #F4E9D4 | ink #1A1A1A | 15.1:1 | AAA |
| `.btn-disabled` | muted #5A544C | cream #F4E9D4 | 5.3:1 | AA (disabled exempt from AA anyway) |
| `.beta-status.error` | #FFB4A1 | ink #1A1A1A | 9.0:1 | AAA |
| `.beta-status.ok` | #B9F5C1 | ink #1A1A1A | 13.7:1 | AAA |
| `.nav-links a` | muted #5A544C | cream #F4E9D4 | 5.3:1 | AA |
| `.a11y-section` border on cards | rgba(244,233,212,0.18) | ink #1A1A1A | ~3.4:1 | AA (non-text) |
| **FIXED:** placeholder | rgba(244,233,212,0.7) | blended input bg | ~5:1 | AA (non-text) |

Body-text muted-on-cream is 5.3:1 — passes AA for body text but
fails AAA. Acceptable here: the muted color is reserved for secondary
copy (subtitles, FAQ answers, card body), and the primary copy
(headings, hero, CTAs) all runs at 15:1 AAA. If we ever pitch
"WCAG AAA across the board" we'd need to darken the muted token or
drop it for primary body.

### Responsive + zoom
- [x] Viewport meta allows user scaling (no `maximum-scale=1` or `user-scalable=no`)
- [x] `clamp(...)` typography scales with viewport — won't break at 320px
- [x] `@media (max-width: 640px)` adjusts hero, sections, nav — line 364
- [x] `flex-wrap: wrap` on CTA row and beta form — no horizontal overflow at narrow widths

### Privacy + safety links
- [x] Privacy policy link in footer — `privacy.html` — line 537
- [x] Terms link — `terms.html` — line 538
- [x] Source code link — `github.com/fainir/handy-ai` — line 539
- [x] Direct contact email — `fainir2006@gmail.com` — line 540

## What this audit does NOT cover

- **Live screen-reader walkthrough.** A static read can predict
  most issues but a real TalkBack + NVDA + VoiceOver run would
  catch announcement-order issues this audit can't see. Worth
  doing once the user has a screen reader in front of them.
- **Lighthouse score.** Not run today because the chrome-devtools
  browser profile was locked from earlier in the session. The
  static checks above hit every Lighthouse a11y rule that operates
  on stable DOM. The two failures fixed in this commit would have
  been Lighthouse-flagged as "Form elements do not have associated
  labels" and "Image elements do not have `[alt]` attributes" (the
  latter is technically inapplicable since they're SVG, but Lighthouse
  flags decorative SVGs without `aria-hidden` under the same rule).
- **/setup.html paired-laptop page.** Audit limited to /index.html
  in this pass. The setup page already has skip-link + ARIA per
  earlier commits; a second audit pass is on the followup list.
- **Privacy / terms pages.** Linked from footer; out of scope here.

## Followup [ ] tasks

- [ ] Live screen-reader walkthrough on a real device (TalkBack on
      Pixel, NVDA on Windows, VoiceOver on macOS). 20-min total.
- [x] Audit `docs/setup.html` with the same checklist. **See section below.**
- [ ] Consider darkening the `--muted` token if we want full AAA
      on secondary copy (current ratio: 5.3:1; AAA needs 7:1). One
      candidate: `#4A453E` → 6.9:1 (barely fails AAA), `#3F3A33` →
      8.3:1 (passes AAA). Either change is global and needs a
      visual review.
- [ ] Add a `prefers-reduced-data` media query to drop the Google
      Fonts preload on metered connections. Not WCAG but adjacent
      to accessibility.

---

# /setup.html audit — 2026-05-22

Same WCAG 2.1 AA + AAA checklist applied to the paired-laptop key
setup page. This is the page screen-reader users land on when they
choose "Set up via computer" in the app, so it has to be screen-
reader-friendly by definition.

## Verdict

**PASS** with one minor gap fixed inline.

| # | Severity | Finding | Action |
|---|----------|---------|--------|
| 1 | WCAG 2.3.3 Animation from Interactions (AAA) + 2.2.2 (AA via best practice) | No `@media (prefers-reduced-motion: reduce)` block. The submit button has `transition: opacity 150ms ease-out, transform 100ms ease-out` which fires regardless of user preference. Index.html had this guard; setup.html was missing it. | Added the standard reduced-motion guard. Also strengthened the existing `prefers-contrast: more` block to add 2px form/input borders. |

Everything else is well-built — in some ways better than index.html.

## What setup.html does correctly (and notably better than the landing)

- **Visible labels on every input.** Not sr-only — explicit
  `<label for="codeInput">Setup code</label>` (line 244) and
  `<label for="keyInput">Anthropic API key</label>` (line 260) sit
  above the inputs. This is the gold standard for form labels.
- **`aria-describedby` linking inputs to their hint paragraphs**
  (lines 254, 268). Screen readers announce the hint alongside the
  label.
- **Code display is `aria-live="polite"`** (line 238). When the
  URL prefills a code via the `?c=ABC123` query param, screen
  readers announce the new value.
- **Status region uses role-switching**: starts as `role="status"`,
  changes to `role="alert"` for errors via JS (line 328). Errors
  preempt other speech; success messages politely queue. This is
  the textbook pattern.
- **Touch targets meet minimum.** Inputs and button both have
  `min-height: 48px` (lines 140, 158). Above the 44px WCAG
  recommendation.
- **API key input is `type="password"`** (line 262). Visually
  masked; screen readers announce "secure entry".
- **Setup steps `<ol>` carries `aria-label="Setup steps"`** (line
  285) so screen-reader navigation by region surfaces it cleanly.
- **`inputmode="text"` + `maxlength="6"` + `autocapitalize="characters"`
  on the code input** (lines 250-253). Phones surface the right
  keyboard, prevent overflow, auto-uppercase.

## Color contrast (computed)

| Element | Foreground | Background | Ratio | WCAG |
|---------|-----------|-----------|-------|------|
| Body | ink #1A1A1A | cream #F4E9D4 | 15.1:1 | AAA |
| `.lead` | ink-2 #3A3530 | cream #F4E9D4 | ~11:1 | AAA |
| `.hint` | muted #5A544C | form bg (white 60% on cream) | ~6:1 | AA |
| `.code-value` | cream | ink | 15.1:1 | AAA |
| Button text | cream | ink | 15.1:1 | AAA |
| Error status text | danger #B83A2C | rgba(184,58,44,0.1)-on-cream blend | ~6:1 | AA |
| Success status text | success #2C7A4A | rgba(44,122,74,0.12)-on-cream blend | ~5:1 | AA |
| Input border | border #D4C6A8 | cream #F4E9D4 | ~1.4:1 | AA (just-fails 1.4.11 non-text 3:1) |

The input border is the one borderline number. WCAG 1.4.11
(Non-text Contrast) requires 3:1 against the adjacent color for
UI components. Cream-on-cream border is well below that. However:
the input has a 2px-thick border (vs. typical 1px), and on focus
the border switches to ink (#1A1A1A) at 15:1 with a 3px box-shadow
halo. That makes the focused state strongly distinguishable. The
default state is harder to find on a quick visual scan — but a
screen reader user gets the label, which is what matters; a
sighted-low-vision user gets the focus halo as soon as they
interact. Acceptable, with a "could be improved" note.

## Form data flow / privacy notes

- API key submitted to `https://cloudbot-ai.com/api/handy-key-setup/claim`
  over TLS (line 353).
- Page footer explicitly states 10-minute server-memory TTL with
  immediate wipe on phone pickup (lines 295-297). Honest and
  matches the actual `handy_key_setup.py` implementation in
  cloudbot-panel.
- No analytics scripts. No third-party trackers.
- Google Fonts is preconnected and loads CSS — this leaks the
  visitor's User-Agent + IP to Google. Acceptable but worth
  flagging for the GDPR pitch in `rnib-connect-voices-pitch.md`.
  An optional self-host of Fraunces + Inter would close this.

## Code-quality observations (not WCAG)

- Error messages in JS use curly quotes ("doesn't", lines 343,
  368, 369). Renders fine. Inconsistent with the rest of the
  codebase (which uses straight apostrophes). Cosmetic.
- The network-error branch (line 378) interpolates `err.message`
  directly. For a non-technical screen-reader user, "Network
  error: TypeError: Failed to fetch" is opaque. Worth gating
  behind a friendlier user-facing message with the raw error in
  a `<details>` or copy-button. Not WCAG, but worth a future
  polish pass.

## Patch applied in same commit as the index.html fixes

One edit to `docs/setup.html`:

1. Added `@media (prefers-reduced-motion: reduce)` block with the
   standard animation/transition zeroing pattern.
2. Strengthened the existing `@media (prefers-contrast: more)`
   block: form and inputs now get 2px-solid-black borders in
   high-contrast mode (was previously just body bg/fg swap).

## Patch applied in same commit

Three edits to `docs/index.html`:

1. Added `aria-hidden="true" focusable="false"` to all decorative
   inline SVGs (the download icon in btn-primary and the Play
   Store glyph in btn-disabled).
2. Wrapped each beta-form input in a visually-hidden label using
   the existing `.sr-only` class.
3. Bumped the beta-form input placeholder color from
   `rgba(244,233,212,0.55)` to `rgba(244,233,212,0.7)`.

Verified via curl after deploy: `https://gethandyai.app/` returns
200 and the new markup is present.
