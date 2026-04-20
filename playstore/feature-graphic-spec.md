# Feature graphic (1024×500 PNG)

The feature graphic is shown at the top of the Play Store listing. Required.

## Design direction (what to hand a designer or generate)

- **Background:** warm cream (#F4E9D4) — matches the launcher icon.
- **Left 40% of canvas:** the "H" monogram from `drawable/ic_logo_h.xml`, scaled to ~320px tall, centered vertically. Keep the small orange dot visible.
- **Right 60% of canvas:** headline in a bold display-serif (Playfair Display 700 or Fraunces 700), 72–80px, dark ink (#1A1A1A), three lines max:

  > Hand your
  > phone a
  > task.

- Tagline below headline: 28px, muted (#5A544C), one line:

  > Voice or text. Handy does it.

- No drop shadows. No purple gradient. No stock phone render — the monogram is the hero.

## Quick generation options

If you want a fast first pass, export a PNG from any of:
- Figma — use the spec above, export 1024×500.
- Canva — create a 1024×500 Custom Size, drop in `playstore/feature-graphic-bg.svg` (below) and type the headline.
- CLI with librsvg + ImageMagick (no Adobe needed) — see `playstore/gen-feature-graphic.sh` once created.

## Minimum acceptable

If you want to ship today and iterate on visuals later: a single cream background with the "H" monogram centered and the words "Handy AI" in large display-serif to the right. No tagline. Still passes Play review.
