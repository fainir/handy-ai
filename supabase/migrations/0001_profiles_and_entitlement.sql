-- Handy AI: profiles + entitlement, reconstructed 2026-08-09.
--
-- READ supabase/REBUILD.md FIRST. The original Supabase project (lahxcictftleizekgzhu) was deleted
-- and its schema was never in version control - it lived only in the dashboard. This file is
-- reconstructed from the code that still calls it, so it is a STARTING POINT, not a restoration.
-- Nobody can diff it against the original, because the original no longer exists.
--
-- Reconstructed from:
--   * supabase/functions/lemonsqueezy-webhook/index.ts  - writes the four subscription columns,
--     keyed on user_id
--   * app/.../EntitlementClient.kt                      - reads is_entitled, trial_days_left,
--     subscription_active from my_entitlement, using the caller's own token
--   * app/.../OnboardingActivity.kt                     - "Sign-in opens the 7-day free trial"

create table if not exists public.profiles (
  user_id                       uuid primary key references auth.users (id) on delete cascade,
  created_at                    timestamptz not null default now(),

  -- Written by the Lemon Squeezy webhook. It sets subscription_active true for the statuses
  -- active / on_trial / past_due / paused, and false for anything else.
  subscription_active           boolean     not null default false,
  subscription_expires_at       timestamptz,
  lemonsqueezy_customer_id      text,
  lemonsqueezy_subscription_id  text
);

-- A profile row must exist before the webhook's UPDATE can match it: the webhook only updates, it
-- never inserts, so a user who checks out before their row exists would silently get no entitlement.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (user_id) values (new.id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;

-- The app queries my_entitlement with the user's access token and no filter, relying entirely on RLS
-- to return only their row. If these policies are missing or too broad, that query leaks every
-- profile in the table.
drop policy if exists "own profile readable" on public.profiles;
create policy "own profile readable"
  on public.profiles for select
  using (auth.uid() = user_id);

-- Deliberately no INSERT/UPDATE policy for end users: the only writer is the webhook, which uses the
-- service-role key and bypasses RLS. A user who could write their own row could grant themselves a
-- subscription.

-- INFERRED: the trial is counted from created_at. The original may have used an explicit
-- trial_ends_at column - this is the part most worth verifying against real behaviour.
create or replace view public.my_entitlement
with (security_invoker = on) as        -- runs as the caller, so the RLS policy above applies
select
  p.user_id,
  p.subscription_active,
  greatest(
    0,
    7 - floor(extract(epoch from (now() - p.created_at)) / 86400)::int
  ) as trial_days_left,
  (
    p.subscription_active
    or (now() - p.created_at) < interval '7 days'
  ) as is_entitled
from public.profiles p;

grant select on public.my_entitlement to authenticated;
