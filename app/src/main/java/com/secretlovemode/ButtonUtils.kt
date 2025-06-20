package com.secretlovemode

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.widget.Button
import kotlin.math.max

object ButtonUtils {

    /**
     * 텍스트 길이에 따라 버튼의 높이와 텍스트 크기를 동적으로 조절
     */
    fun adjustButtonForText(button: Button, text: String) {
        val context = button.context

        // 텍스트 길이 계산
        val textLength = text.length
        val lineCount = calculateLineCount(text, button)

        // 기본 설정
        val baseTextSize = 16f // sp
        val baseHeight = dpToPx(context, 56f) // 기본 높이
        val maxTextSize = 18f
        val minTextSize = 12f

        // 텍스트 크기 조절 (길수록 작게)
        val adjustedTextSize = when {
            textLength <= 10 -> maxTextSize
            textLength <= 20 -> baseTextSize
            textLength <= 30 -> 14f
            else -> minTextSize
        }.coerceIn(minTextSize, maxTextSize)

        // 높이 조절 (여러 줄이면 높게)
        val adjustedHeight = when {
            lineCount <= 1 -> baseHeight
            lineCount == 2 -> (baseHeight * 1.4f).toInt()
            else -> (baseHeight * 1.8f).toInt()
        }

        // 패딩 조절
        val verticalPadding = when {
            lineCount <= 1 -> dpToPx(context, 12f)
            lineCount == 2 -> dpToPx(context, 16f)
            else -> dpToPx(context, 20f)
        }

        // 적용
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, adjustedTextSize)
        button.layoutParams.height = adjustedHeight
        button.setPadding(
            button.paddingLeft,
            verticalPadding,
            button.paddingRight,
            verticalPadding
        )

        // 애니메이션 효과
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
     * 텍스트가 몇 줄을 차지할지 계산
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
     * dp를 px로 변환
     */
    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * 버튼들의 크기를 균일하게 맞춤
     */
    fun balanceButtonSizes(buttons: List<Button>) {
        if (buttons.isEmpty()) return

        // 가장 긴 텍스트 찾기
        val maxTextLength = buttons.maxOfOrNull { it.text.length } ?: 0
        val maxLineCount = buttons.maxOfOrNull { calculateLineCount(it.text.toString(), it) } ?: 1

        // 모든 버튼을 가장 큰 요구사항에 맞춤
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