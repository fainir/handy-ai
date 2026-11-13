package com.claudeagent.phone.dialer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {
    @Test fun acceptsInternationalAndCarrierNumbers() {
        assertEquals("+972501234567", PhoneNumbers.normalize("+972 (50) 123-4567"))
        assertEquals("+12025550101", PhoneNumbers.normalize("+١٢٠٢ ٥٥٥ ٠١٠١"))
        assertEquals("*123#", PhoneNumbers.normalize("*123#"))
        assertEquals("112", PhoneNumbers.normalize("112"))
    }

    @Test fun rejectsAmbiguousOrOversizeDialLinks() {
        listOf("", " ", "+", "12+34", "tel:+123", "555,911", "555;123", "5/5", "5\u202e5", "5".repeat(41))
            .forEach { assertNull(it, PhoneNumbers.normalize(it)) }
    }

    @Test fun keypadAllowsPlusOnlyAtStartAndCapsLength() {
        assertEquals("+", PhoneNumbers.append("", '+'))
        assertEquals("+1", PhoneNumbers.append("+", '1'))
        assertEquals("1", PhoneNumbers.append("1", '+'))
        assertEquals("123", PhoneNumbers.append("123", 'x'))
        val full = "0".repeat(PhoneNumbers.MAX_LENGTH)
        assertEquals(full, PhoneNumbers.append(full, '1'))
    }
}
