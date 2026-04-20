# Hub ↔ Phone protocol spec

This document is the contract between the Handy AI Android app and the Cloudbot hub
at `https://best-agent-hub-production.up.railway.app`. The app already calls these
endpoints; the hub needs to implement them.

Everything below lives on the hub. Nothing new ships on the phone besides what's
already in `HubClient.kt`.

---

## 1. Phone pairing

Goal: a signed-in Cloudbot user taps "Add phone" on the web dashboard and enters a
6-character code displayed on their phone. The phone then gets a long-lived
`device_token` it uses to authenticate on the control channel.

### POST /api/phone/pair/init

Called by the phone with no auth.

Body:
```json
{ "platform": "android", "model": "Pixel 10" }
```

Response 200:
```json
{ "pair_code": "H4-8K2Z", "expires_at": "2026-04-20T21:10:00Z" }
```

Server stores: `pair_code → (status=pending, created_at, platform, model)`, TTL 10 min.

### GET /api/phone/pair/status?code=H4-8K2Z

Called by the phone every 3s. No auth.

Response 200:
```json
{ "status": "pending" }
```
or
```json
{ "status": "claimed", "device_token": "pht_..." }
```
or
```json
{ "status": "expired" }
```

Once the code transitions to `claimed`, the server should return `device_token`
once, then subsequent polls should go back to returning `claimed` without the
token (or 410 Gone). `device_token` is long-lived; store it hashed on the hub.

### POST /api/phone/pair/claim  (web dashboard)

Called by the signed-in web user when they submit the code.

Body:
```json
{ "pair_code": "H4-8K2Z", "name": "My Pixel 10" }
```

Response 200:
```json
{ "device_id": "dev_...", "device_token": "pht_..." }
```

Server: mark pair_code claimed, associate `device_id` with the user's account,
set `status=claimed`, store `device_token` for the phone to pick up.

---

## 2. Remote control channel

Once paired, the phone opens a WebSocket and keeps it alive. Cloudbot users (or
any MCP-connected agent — see §3) send it commands through the hub.

### WSS /api/phone/ws?token=<device_token>

Frames are JSON, one per line. No compression.

Phone → hub:
```json
{ "type": "hello", "app_version": "1.0", "screen_w": 1080, "screen_h": 2400 }
{ "type": "ack",   "id": "cmd_123" }
{ "type": "done",  "id": "cmd_123", "success": true, "summary": "Opened Chrome and searched weather" }
{ "type": "event", "kind": "screenshot", "id": "cmd_123", "png_base64": "..." }
{ "type": "error", "id": "cmd_123", "message": "accessibility_revoked" }
```

Hub → phone:
```json
{ "type": "run",    "id": "cmd_123", "task": "Open Chrome, search weather Tel Aviv" }
{ "type": "stop",   "id": "cmd_123" }
{ "type": "screenshot", "id": "cmd_124" }
{ "type": "tool",   "id": "cmd_125", "name": "tap", "args": { "x": 540, "y": 1200 } }
```

Two modes the phone MUST support:

- **Task mode**: hub sends `{type:"run", task}`, phone runs the existing
  `AgentLoop` on-device (with its own Anthropic key or an ephemeral key
  from the hub — see §4) and streams screenshot events + a final `done`.
- **Tool mode**: hub acts as the brain, sends individual `{type:"tool"}`
  commands, phone executes them and returns a fresh screenshot. This is
  what MCP-connected agents (Claude Code, Codex, Cloudbot) will use.

### Ephemeral key issue

Subscribed users don't have an Anthropic API key of their own. For Task
mode to work server-side, the hub either:

a. Issues an ephemeral, short-lived (5 min) proxy key scoped to the phone
   on request. Phone calls `POST /api/phone/ephemeral-key` with its
   `device_token`, gets back `{ api_key, expires_at }`, uses it, discards it.
b. Proxies the Anthropic call itself (phone posts the message payload to
   `/api/phone/anthropic-proxy`, hub forwards to Anthropic with its own
   key, streams response back).

Option (b) is simpler and avoids leaking any real keys. Option (a) means
less server bandwidth. Recommend (b) for v1.

---

## 3. MCP server

Expose phones as an MCP server so agents like Claude Code, Codex, Cursor
can drive them.

### Endpoint

```
GET /api/mcp/phones?user_token=...
```

Lists devices the user owns:
```json
[ { "device_id": "dev_abc", "name": "My Pixel 10", "online": true } ]
```

### Tools exposed by the MCP server (one-per-device scope)

| Tool | Arguments | Notes |
|---|---|---|
| `phone.screenshot` | `device_id` | Triggers WS `{type:"screenshot"}`, returns PNG |
| `phone.tap` | `device_id, x, y` | Dispatches to phone |
| `phone.swipe` | `device_id, x1, y1, x2, y2, duration_ms` | |
| `phone.type_text` | `device_id, text` | |
| `phone.key` | `device_id, action` | action ∈ {back, home, recents, ime_enter} |
| `phone.wait` | `device_id, ms` | |
| `phone.run_task` | `device_id, task` | Kick off on-device agent loop |
| `phone.stop_task` | `device_id` | |

All calls authenticate via a scoped MCP token the user creates on the
dashboard.

### Claude Code integration

Once the MCP endpoint exists, users add it with one line:
```
claude mcp add handy-ai --url https://best-agent-hub-production.up.railway.app/api/mcp/phones --header "Authorization: Bearer $HANDY_MCP_TOKEN"
```

and Claude Code can then call `phone.screenshot`, `phone.tap`, etc.

---

## 4. Anthropic proxy (recommended)

### POST /api/phone/anthropic/messages

Headers: `Authorization: Bearer <device_token>`
Body: a passthrough `messages` request for the Claude API.

Hub validates the device_token, checks the account's subscription state
(and trial expiry), then forwards to `api.anthropic.com/v1/messages` with
the hub's own API key. Response is streamed back unchanged.

Rate-limit per device: 60 req/min, 100k tokens/day during trial, ∞
during paid. Return 402 if the account is past trial and unpaid.

The phone app can switch between direct-to-Anthropic (BYO key mode) and
hub-proxied (subscription mode) just by picking the base URL in
`AnthropicClient`. Today it's hard-coded to Anthropic; the migration is
one-line once this endpoint lives.

---

## 5. Minimum MVP to ship

For the Play Store launch the phone app does NOT need any of this to
work — BYO API key mode is fully self-contained. To ship the
subscription path end-to-end, the hub needs:

1. `POST /api/phone/pair/init`
2. `GET /api/phone/pair/status`
3. `POST /api/phone/pair/claim` (+ web dashboard UI for the user to enter the code)
4. `POST /api/phone/anthropic/messages`

Everything in §3 (MCP) is a separate later phase.

---

## 6. Security notes

- `device_token` is bearer; on revoke, the dashboard deletes it and the
  phone's WS disconnects on next heartbeat.
- `pair_code` is single-use and expires in 10 min. Rate-limit claims to
  5/min per IP to prevent brute force (codes are only ~36^6 = 2B).
- Don't log PNG screenshots to the hub disk. Stream them through.
- TLS only. No unencrypted WS.
