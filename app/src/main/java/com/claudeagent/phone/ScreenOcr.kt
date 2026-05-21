package com.claudeagent.phone

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Local, free, on-device OCR for the "Read me the screen" flow.
 * Wraps Google ML Kit's text recognizer.
 *
 * A blind user asking "what does this notification say?" shouldn't
 * burn Claude tokens on a problem that's just text-on-screen. We OCR
 * locally with ML Kit, hand the resulting text back, and let the
 * AccessibilityHelper.speak() pipeline narrate it instantly.
 */
object ScreenOcr {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(bitmap: Bitmap, region: Rect? = null): String {
        val input = if (region != null) {
            val cropped = Bitmap.createBitmap(
                bitmap,
                region.left.coerceAtLeast(0),
                region.top.coerceAtLeast(0),
                region.width().coerceAtMost(bitmap.width - region.left),
                region.height().coerceAtMost(bitmap.height - region.top),
            )
            InputImage.fromBitmap(cropped, 0)
        } else {
            InputImage.fromBitmap(bitmap, 0)
        }
        return suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val cleaned = result.text
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString("\n")
                    cont.resume(cleaned)
                }
                .addOnFailureListener { _ ->
                    // Never throw from here — accessibility flows degrade
                    // gracefully. The caller phrases "I couldn't read the
                    // screen" to the user.
                    cont.resume("")
                }
        }
    }
}
