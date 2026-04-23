# Tasks

## In progress
- Deploy cloudbot-panel → Railway (just pushed commit `019fa23` — waiting for /api/phone to appear at https://cloudbot-ai.com/api)

## Up next (once deploy is live)
- Smoke-test /api/phone: pair/init → pair/claim → pair/status → /me → DELETE /{id}
- Wire Android `PanelClient.kt` so Settings can pair with cloudbot-panel (separate from existing HubClient)
- Monitor Play Console review — all 14 declarations sent earlier; waiting on Google (typ. ≤7 days)

## Done this session
- AAB rebuilt + uploaded as v1.4.1 (versionCode 6, targetSdk 35); shadowed v5 removed
- Play Console accessibility disclosure video recorded/edited/uploaded (YouTube unlisted)
- App access restricted with reviewer API key + instructions
- All 14 pending changes sent for review
- keySetupBar new-user page validated + marked done
- Phone + PhonePairCode models added to cloudbot-panel
- /api/phone router built (pair/init, pair/status, pair/claim, list, me, delete)
- Router registered + committed + pushed to main
