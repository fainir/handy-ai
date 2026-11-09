# Handy AI 1.5.7 trial acceptance

The included path uses the existing Betterloop Claude credential through the guarded gateway. The APK contains a gateway URL. The personal key is saved through Android Keystore and goes to Anthropic when the user chooses the personal-key path.

- [Gateway PR](https://github.com/fainir/betterloop/pull/33) passed local typecheck, build, 332 PostgreSQL checks and the multi-process quota test. The new tests reject an invalid image before reservation, refuse the eleventh step across billing windows and networks, and keep an unauthenticated provider error from pausing the shared gateway.
- Android JVM tests exercise the native request, response and Stop behavior over loopback HTTP, and verify coordinate conversion from the resized screenshot. They do not dispatch a real Claude request.
- Chromium rendered the staged homepage, privacy policy and terms at 375px and 1440px. All three had matching trial copy and no horizontal overflow. [Mobile privacy screenshot](privacy-375.png).
- Store disclosure and release text are staged in [Play release notes](../playstore/release-notes-v1.5.7.md).
