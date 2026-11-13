package com.claudeagent.phone.dialer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.claudeagent.phone.AccessibilityHelper
import com.claudeagent.phone.R
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.phoneColor(id: Int): Int = ContextCompat.getColor(this, id)

internal fun Context.phoneBackground(id: Int): Int =
    phoneColor(if (AccessibilityHelper.isHighContrast(this)) R.color.black else id)

internal fun Context.phoneText(id: Int): Int =
    phoneColor(if (AccessibilityHelper.isHighContrast(this)) R.color.white else id)

internal fun Context.phoneLabel(@StringRes label: Int, size: Float = 15f): TextView =
    TextView(this).apply {
        setText(label)
        textSize = size
        setTextColor(phoneText(R.color.phone_text))
    }

internal fun Context.phoneButton(@StringRes label: Int): MaterialButton =
    MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        setText(label)
        setAllCaps(false)
        minimumHeight = dp(48)
        minHeight = dp(48)
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(20)
        setTextColor(phoneText(R.color.phone_text))
        backgroundTintList = ColorStateList.valueOf(phoneBackground(R.color.phone_panel))
        strokeColor = ColorStateList.valueOf(phoneText(R.color.phone_outline))
        strokeWidth = dp(1)
    }

internal fun MaterialButton.phoneFill(colorId: Int) {
    backgroundTintList = ColorStateList.valueOf(context.phoneText(colorId))
    setTextColor(context.phoneColor(R.color.phone_bg))
    strokeWidth = 0
}

internal fun Context.phonePanel(): GradientDrawable = GradientDrawable().apply {
    setColor(phoneBackground(R.color.phone_panel))
    cornerRadius = dp(20).toFloat()
}

/** Phone keypad and active-call DTMF keypad share their layout and focus order. */
internal fun LinearLayout.fillPhoneKeys(labels: List<String>, compact: Boolean = false, onKey: (Char) -> Unit) {
    val context = context
    labels.chunked(3).forEach { keys ->
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER }
        addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
        keys.forEach { label ->
            val key = context.phoneButton(R.string.phone_keypad).apply {
                text = label
                textSize = if (compact) 20f else 24f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                contentDescription = label
                setOnClickListener { onKey(label.single()) }
                if (label == "0" && !compact) {
                    contentDescription = context.getString(R.string.phone_key_zero)
                    setOnLongClickListener {
                        onKey('+')
                        true
                    }
                }
            }
            row.addView(key, LinearLayout.LayoutParams(0, context.dp(if (compact) 48 else 56), 1f).apply {
                marginStart = context.dp(3)
                marginEnd = context.dp(3)
            })
        }
    }
}

internal val phoneKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

@Suppress("DEPRECATION")
internal fun View.announcePhone(@StringRes message: Int) = announceForAccessibility(context.getString(message))
