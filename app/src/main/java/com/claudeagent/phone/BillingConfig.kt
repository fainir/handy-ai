package com.claudeagent.phone

/**
 * Static configuration. All secret-ish values come from BuildConfig so they
 * can be populated from `local.properties` (gitignored) without touching
 * source. See the repo's `local.properties.example` for the full list.
 */
object BillingConfig {
    // TODO(user): replace with your Lemon Squeezy store/variant once the product is created.
    const val LEMONSQUEEZY_CHECKOUT_URL =
        "https://handyai.lemonsqueezy.com/buy/PLACEHOLDER_VARIANT_ID?embed=0&media=0"

    /**
     * Build a checkout URL with the signed-in user's Supabase id threaded
     * through as `checkout[custom][user_id]`. The webhook reads this to know
     * which profiles row to update when LS sends `subscription_*` events.
     * Falls back to the raw URL if no user is signed in (shouldn't happen
     * in the normal flow since we require sign-in before showing checkout).
     */
    fun checkoutUrlFor(userId: String?): String {
        if (userId.isNullOrBlank()) return LEMONSQUEEZY_CHECKOUT_URL
        val sep = if (LEMONSQUEEZY_CHECKOUT_URL.contains('?')) "&" else "?"
        // URL-encode the brackets; LS accepts both but encoded is safer in intents.
        val encoded = java.net.URLEncoder.encode("checkout[custom][user_id]", "UTF-8")
        return "$LEMONSQUEEZY_CHECKOUT_URL$sep$encoded=$userId"
    }

    const val PRICE_LABEL = "\$9.99 / month"
    const val TRIAL_LABEL = "7 days free"

    // Hub base URL — used for device pairing and remote control channel.
    const val HUB_BASE_URL = "https://best-agent-hub-production.up.railway.app"

    // ---- Supabase (trial sign-in) ----
    //
    // Populate via local.properties:
    //   SUPABASE_URL=https://xxx.supabase.co
    //   SUPABASE_ANON_KEY=eyJ...
    //
    // Anon key is intentionally client-safe (Row Level Security on the server
    // enforces access). DO NOT put the service_role key here.
    val SUPABASE_URL: String get() = BuildConfig.SUPABASE_URL
    val SUPABASE_ANON_KEY: String get() = BuildConfig.SUPABASE_ANON_KEY

    fun supabaseConfigured(): Boolean =
        !SUPABASE_URL.contains("YOUR_PROJECT") &&
            !SUPABASE_ANON_KEY.contains("YOUR_ANON_KEY") &&
            SUPABASE_URL.isNotBlank() &&
            SUPABASE_ANON_KEY.isNotBlank()

    // ---- Google sign-in (optional; layered on top of Supabase) ----
    //
    // Populate via local.properties:
    //   GOOGLE_WEB_CLIENT_ID=1234567890-xxxxxxx.apps.googleusercontent.com
    //
    // This is the **Web** OAuth client ID (not the Android client). Supabase
    // → Authentication → Providers → Google must have this same client ID
    // registered so the ID token we forward verifies server-side.
    val GOOGLE_WEB_CLIENT_ID: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun googleSignInConfigured(): Boolean =
        supabaseConfigured() && GOOGLE_WEB_CLIENT_ID.isNotBlank()
}
