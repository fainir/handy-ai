# Handy AI Phone development

## Strategy

- Diagnosis: the AI Phone design has 11 views. Carrier calls need one default Android dialer that can answer and end calls before AI participates.
- Guiding policy: retain Handy AI chat and build calling on Android Telecom. Treat access to live call speech as a device experiment.
- Coherent actions: establish role and call control (F1–F3), support local contacts and history (F4), validate an explicit AI draft (F5), and test live speech before release (F6–F7).
- Feasible because: the project already builds against Android 36. The first test uses an emulator and the carrier test uses the owner's Pixel 10.
- Next objective: exercise one incoming and one outgoing simulated call by November 13, 2026.
- Review by: 2026-11-16

## Processes

| ID | Process | When | Runner | How | Done when | Last run | Health |
|---|---|---|---|---|---|---|---|
| P1 | Build the next calling flow | every run | session | PRODUCT.md | Build and tests exercise the changed flow | 2026-11-13 17:06 | ok |
| P2 | Test the dialer on Android | before release | session | qa/handy-phone.md | Outgoing and incoming test calls pass | - | new |
| P3 | Verify a SIM call on Pixel | before release | session | READINESS.md | Carrier audio, locked-screen alert and caller ID verified | - | new |
| P4 | Verify release and permissions | before release | session | AGENTS.md | Local CI passes and Play disclosures match the app | - | new |

## Lanes

| Lane | Covers | Runner | Owns | WIP | Waits on |
|---|---|---|---|---|---|
| Android | Role, calls and call UI | session | app/src/main/ | 1 | - |
| Verification | Emulator and device checks | session | qa/ | 1 | Android |
