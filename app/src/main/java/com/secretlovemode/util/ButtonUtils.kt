package com.secretlovemode.util

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.widget.Button
import kotlin.math.max

object ButtonUtils {

    /**
     * Dynamically adjusts button height and text size based on text length
     */
    fun adjustButtonForText(button: Button, text: String) {
        val context = button.context

        // Calculate text length
        val textLength = text.length
        val lineCount = calculateLineCount(text, button)

        // Base settings
        val baseTextSize = 16f // sp
        val baseHeight = dpToPx(context, 56f) // Base height
        val maxTextSize = 18f
        val minTextSize = 12f

        // Adjust text size (smaller for longer text)
        val adjustedTextSize = when {
            textLength <= 10 -> maxTextSize
            textLength <= 20 -> baseTextSize
            textLength <= 30 -> 14f
            else -> minTextSize
        }.coerceIn(minTextSize, maxTextSize)

        // Adjust height (taller for multiple lines)
        val adjustedHeight = when {
            lineCount <= 1 -> baseHeight
            lineCount == 2 -> (baseHeight * 1.4f).toInt()
            else -> (baseHeight * 1.8f).toInt()
        }

        // Adjust padding
        val verticalPadding = when {
            lineCount <= 1 -> dpToPx(context, 12f)
            lineCount == 2 -> dpToPx(context, 16f)
            else -> dpToPx(context, 20f)
        }

        // Apply
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjustedTextSize)
        button.layoutParams.height = adjustedHeight
        button.setPadding(
            button.paddingLeft,
            verticalPadding,
            button.paddingRight,
            verticalPadding
        )

        // Animation effect
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                button.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    /**
     * Calculates how many lines the text will occupy
     */
    private fun calculateLineCount(text: String, button: Button): Int {
        val paint = Paint()
        paint.textSize = button.textSize

        val maxWidth = button.width - button.paddingLeft - button.paddingRight
        if (maxWidth <= 0) return 1

        val textWidth = paint.measureText(text)
        return max(1, (textWidth / maxWidth).toInt() + 1)
    }

    /**
     * Converts dp to px
     */
    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * Balances the sizes of buttons uniformly
     */
    fun balanceButtonSizes(buttons: List<Button>) {
        if (buttons.isEmpty()) return

        // Find the longest text
        val maxTextLength = buttons.maxOfOrNull { it.text.length } ?: 0
        val maxLineCount = buttons.maxOfOrNull { calculateLineCount(it.text.toString(), it) } ?: 1

        // Adjust all buttons to meet the largest requirement
        buttons.forEach { button ->
            val textLength = button.text.length
            val baseTextSize = when {
                maxTextLength <= 15 -> 16f
                maxTextLength <= 25 -> 14f
                else -> 12f
            }

            val baseHeight = when {
                maxLineCount <= 1 -> dpToPx(button.context, 56f)
                maxLineCount == 2 -> dpToPx(button.context, 78f)
                else -> dpToPx(button.context, 100f)
            }

            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseTextSize)
            button.layoutParams.height = baseHeight
            button.requestLayout()
        }
    }
}