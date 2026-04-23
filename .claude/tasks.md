# Tasks

## In progress
- Await Railway deploy of /api/phone/pair/init rate-limit (monitor armed; 11th hit → 429)

## Blocked (external)
- Google Play review — 14 changes sent for review on v1.4.1. Typ. ≤7 days. Check fainir2006@gmail.com + Play Console Unread notifications.

## Shipped this session
### Play Store
- v1.4.1 AAB uploaded to Closed Testing (versionCode 6, targetSdk 35)
- Accessibility Services disclosure video (YouTube unlisted) — URL in declaration
- Advertising ID = No
- App access = restricted with reviewer API key + 3-step instructions
- All 14 pending changes sent for review

### Backend (cloudbot-panel)
- `Phone` + `PhonePairCode` models + migration 002
- `/api/phone` router: POST /pair/init, GET /pair/status/{code}, POST /pair/claim, GET "", GET /me, DELETE /{id}
- SHA-256 token hashing, one-time token delivery, expire-on-read
- Per-IP rate-limit on /pair/init (10/min)
- Frontend `/phones` page: list + add-phone modal + unpair
- Nav link from main dashboard
- Deployed to cloudbot-ai.com (via `railway up` — git auto-deploy was off)

### Android (handy-ai)
- `PanelClient.kt` with initPanelPair / pollPanelStatus / panelMe / isPaired
- Sealed `MeResult` (Success/Revoked/Offline) so heartbeat can react correctly
- `PanelPairActivity` + `activity_panel_pair.xml` mirrors Hub pair flow
- Settings "CloudBot panel" section — coexists with the hub pairing
- `UserState` gets panelPairCode / panelToken / panelPhoneId slots
- `HandyAIApplication` fires heartbeat on launch (clears local state on 401)
- v1.4.2 signed release APK (versionCode 7) staged at docs/HandyAI.apk

## Up next (optional)
- End-to-end manual test: sign into cloudbot-ai.com, pair Handy AI app from the v1.4.2 APK, verify claim → token delivery → Phones list shows it
- Add panel-side fleet view for remote task dispatch (future)
- Investigate why Railway git auto-deploy stopped working ~Feb 18 (needed `railway up` for both this session's deploys)
