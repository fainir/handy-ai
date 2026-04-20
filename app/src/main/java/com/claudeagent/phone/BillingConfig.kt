package com.claudeagent.phone

object BillingConfig {
    // TODO(user): replace with your Lemon Squeezy store/variant once the product is created.
    // This is the product variant checkout URL. Users are redirected here to start the
    // free trial / subscription.
    const val LEMONSQUEEZY_CHECKOUT_URL =
        "https://handyai.lemonsqueezy.com/buy/PLACEHOLDER_VARIANT_ID?embed=0&media=0"

    // Optional store hint shown on the subscribe screen.
    const val PRICE_LABEL = "\$9.99 / month"
    const val TRIAL_LABEL = "7 days free"

    // Hub base URL — used for device pairing and remote control channel.
    const val HUB_BASE_URL = "https://best-agent-hub-production.up.railway.app"
}
