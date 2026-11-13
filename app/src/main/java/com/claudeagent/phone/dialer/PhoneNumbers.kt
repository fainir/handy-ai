package com.claudeagent.phone.dialer

/** Only a dial string goes to Telecom. A link never places a call by itself. */
internal object PhoneNumbers {
    const val MAX_LENGTH = 40
    private const val DIAL_CHARS = "0123456789*#+"

    fun normalize(input: String): String? {
        if (input.length > MAX_LENGTH * 2) return null
        val number = buildString {
            for (char in input) {
                val digit = char.digitToIntOrNull()
                when {
                    digit != null -> append(digit)
                    char in "+*#" -> append(char)
                    char.isWhitespace() || char in "()-." -> Unit
                    else -> return null
                }
                if (length > MAX_LENGTH) return null
            }
        }
        return number.takeIf { it.any(Char::isDigit) && '+' !in it.drop(1) }
    }

    fun append(input: String, key: Char): String {
        if (key !in DIAL_CHARS || input.length >= MAX_LENGTH || key == '+' && input.isNotEmpty()) {
            return input
        }
        return input + key
    }
}
