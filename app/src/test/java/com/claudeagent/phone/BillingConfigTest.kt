package com.claudeagent.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BillingConfigTest {

    @Test
    fun placeholderCheckoutCountsAsNotConfigured() {
        // The Lemon Squeezy product was never created; this URL 404s, so Settings hides the buttons that open it.
        assertFalse(BillingConfig.checkoutConfigured())
    }

    @Test
    fun checkoutUrlCarriesTheUserId() {
        assertEquals(BillingConfig.LEMONSQUEEZY_CHECKOUT_URL, BillingConfig.checkoutUrlFor(null))
        assertEquals(
            BillingConfig.LEMONSQUEEZY_CHECKOUT_URL + "&checkout%5Bcustom%5D%5Buser_id%5D=u-123",
            BillingConfig.checkoutUrlFor("u-123"),
        )
    }
}
