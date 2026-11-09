# Handy AI

## Working here

- Verify with `bash scripts/ci-local.sh` using JDK 17 and Android SDK 36. Verify on a test Android profile. `-PisolatedQa=true` gives the debug app a separate package for side-by-side testing.
- The existing Play upload key is local and ignored. Never put the owner Anthropic key, signing credentials, device screenshots with personal content or private QA data in Git.
- The included Claude path uses the Betterloop profile. Any candidate under review is separate from the current Play build. Keep Play's accessibility and data-safety declarations consistent with actual behavior.
