// Lemon Squeezy → Supabase webhook
//
// Flips profiles.subscription_active (and expiry) whenever LS tells us a
// user's subscription changed. Deployed as a public Supabase Edge Function:
//
//   supabase functions deploy lemonsqueezy-webhook --no-verify-jwt
//
// Environment variables to set in Supabase → Edge Functions → Secrets:
//   LEMONSQUEEZY_SIGNING_SECRET  (from the webhook config on LS)
//   SUPABASE_URL                  (auto-populated on Supabase-hosted edge fns)
//   SUPABASE_SERVICE_ROLE_KEY     (auto-populated)
//
// Webhook URL to paste into Lemon Squeezy:
//   https://lahxcictftleizekgzhu.supabase.co/functions/v1/lemonsqueezy-webhook
//
// Events we listen to: subscription_created, subscription_updated,
// subscription_cancelled, subscription_resumed, subscription_expired,
// subscription_paused, subscription_unpaused.
//
// The payload's `meta.custom_data.user_id` is set by the app when it opens
// the checkout URL with `?checkout[custom][user_id]=<supabase user id>` —
// that's how we know which profile row to update.

// deno-lint-ignore-file no-explicit-any
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

// HMAC-SHA256 in the Web Crypto API. LS signs requests with the signing
// secret; we verify before trusting the payload.
async function verifySignature(
  rawBody: string,
  signature: string,
  secret: string,
): Promise<boolean> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(rawBody));
  const hex = Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  // Constant-time comparison to resist timing attacks.
  if (hex.length !== signature.length) return false;
  let diff = 0;
  for (let i = 0; i < hex.length; i++) {
    diff |= hex.charCodeAt(i) ^ signature.charCodeAt(i);
  }
  return diff === 0;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const signingSecret = Deno.env.get("LEMONSQUEEZY_SIGNING_SECRET");
  if (!signingSecret) {
    return new Response("Server misconfigured: missing signing secret", {
      status: 500,
    });
  }

  const rawBody = await req.text();
  const signature = req.headers.get("x-signature") ?? "";
  if (!signature || !(await verifySignature(rawBody, signature, signingSecret))) {
    return new Response("Invalid signature", { status: 401 });
  }

  let payload: any;
  try {
    payload = JSON.parse(rawBody);
  } catch {
    return new Response("Invalid JSON", { status: 400 });
  }

  const eventName: string = payload?.meta?.event_name ?? "";
  const customUserId: string | undefined = payload?.meta?.custom_data?.user_id;
  const attrs = payload?.data?.attributes ?? {};
  const subscriptionId: string | undefined = payload?.data?.id;
  const customerId: string | undefined = attrs.customer_id?.toString();
  const status: string = attrs.status ?? "";
  const endsAt: string | null = attrs.ends_at ?? attrs.renews_at ?? null;

  if (!customUserId) {
    return new Response("Missing custom_data.user_id in payload", { status: 400 });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // Derive active flag from the status field. `active`, `on_trial`, `past_due`
  // (LS still grants access while they retry payment), `paused` (grace period)
  // all keep the sub alive. `cancelled`/`expired`/`unpaid` do not.
  const activeStatuses = new Set([
    "active",
    "on_trial",
    "past_due",
    "paused",
  ]);
  const subscriptionActive = activeStatuses.has(status);

  const { error } = await supabase
    .from("profiles")
    .update({
      subscription_active: subscriptionActive,
      subscription_expires_at: endsAt,
      lemonsqueezy_customer_id: customerId ?? null,
      lemonsqueezy_subscription_id: subscriptionId ?? null,
    })
    .eq("user_id", customUserId);

  if (error) {
    console.error("profile update failed", error);
    return new Response(`DB error: ${error.message}`, { status: 500 });
  }

  console.log(
    `[lemonsqueezy-webhook] ${eventName} user=${customUserId} status=${status} active=${subscriptionActive}`,
  );
  return new Response(JSON.stringify({ ok: true }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
});
