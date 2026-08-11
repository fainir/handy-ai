# Rebuilding the Handy AI Supabase backend

The Supabase project this app was built against — `lahxcictftleizekgzhu` — **no longer exists**.
It has no DNS record at all (a *paused* project still resolves; this one does not), so it was
deleted rather than suspended.

`SUPABASE_URL` is compiled into the app via `buildConfigField`, so **every installed copy, including
the Play Store build, is pointing at a host that does not answer.**

## What that actually breaks

| Feature | Endpoint | Effect today |
|---|---|---|
| Email OTP sign-in | `POST /auth/v1/otp`, `/auth/v1/verify` | dead — nobody can sign in |
| Google sign-in | `POST /auth/v1/token?grant_type=id_token` | dead |
| Session refresh | `POST /auth/v1/token?grant_type=refresh_token` | dead |
| Sign out | `POST /auth/v1/logout` | dead |
| Paid entitlement | `GET /rest/v1/my_entitlement` | dead |
| Lemon Squeezy webhook | `functions/v1/lemonsqueezy-webhook` | dead |

**The app still runs.** `EntitlementClient.fetch()` returns `null` on any failure and its contract is
explicitly *"callers should treat null as don't block"*, so the paywall fails **open**. Nobody is
locked out of the app — but equally, nobody can sign in, and nobody is being gated on payment.

## The problem with rebuilding

Only `supabase/functions/lemonsqueezy-webhook/index.ts` is in git. The **table, the view, the RLS
policies and the OTP email template were only ever configured in the Supabase dashboard**, so when the
project went, the schema went with it. Everything below is reconstructed from the surviving callers.

### Known for certain (read off the code that calls it)

- `profiles` is keyed by `user_id` — the webhook does `.eq("user_id", customUserId)`, where
  `customUserId` arrives as `meta.custom_data.user_id` from the checkout URL.
- The webhook writes exactly four columns: `subscription_active`, `subscription_expires_at`,
  `lemonsqueezy_customer_id`, `lemonsqueezy_subscription_id`.
- It treats `active`, `on_trial`, `past_due` and `paused` as subscription-active.
- `my_entitlement` returns at least `is_entitled`, `trial_days_left`, `subscription_active`
  (`EntitlementClient` reads those three by name).
- It is queried as `?select=*&limit=1` with the **user's own access token**, so it must be
  RLS-scoped to the caller — it returns that user's row without any filter in the URL.
- The trial is **7 days**, starting at sign-in (`OnboardingActivity`: "Sign-in opens the 7-day free
  trial").

### Inferred — verify before trusting

- The column the trial counts from. `trial_days_left` has to derive from *something*, and
  `profiles.created_at` is the natural candidate, but the original may have used an explicit
  `trial_ends_at`. The SQL below uses `created_at` and is marked accordingly.
- Exact column types, and whether `profiles.id` was separate from `user_id`.
- Whether `my_entitlement` was a view or a security-definer function.

## Reconstructed schema

See `migrations/0001_profiles_and_entitlement.sql` in this directory. It is a **starting point, not a
restoration** — nobody can diff it against the original, because the original is gone.

## Also gone, and not in git

- The Handy-AI-branded OTP email template (was configured in the Supabase dashboard).
- Google OAuth provider config (client id/secret in Auth settings).
- The Lemon Squeezy webhook secret (`LEMONSQUEEZY_SIGNING_SECRET` in Edge Function secrets).
- Whatever user rows existed. Those are unrecoverable.

## If you rebuild

1. Create a project, apply the migration, re-add the Google provider and the OTP template.
2. Deploy the function: `supabase functions deploy lemonsqueezy-webhook --no-verify-jwt`.
3. Point the Lemon Squeezy webhook at the new
   `https://<ref>.supabase.co/functions/v1/lemonsqueezy-webhook`.
4. Update `SUPABASE_URL` / `SUPABASE_ANON_KEY` in `local.properties` and **ship a new APK** — the
   values are compiled in, so no existing install picks up a new project without an update.

Step 4 is the one that bites: the backend can be perfect and every shipped phone still talks to the
dead host until its user updates.
