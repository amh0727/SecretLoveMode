package com.secretlovemode.ui.game

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.secretlovemode.ui.common.ParticleView
import com.secretlovemode.util.LanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AnimationManager(
    private val activity: GameActivity,
    private val lifecycleOwner: LifecycleOwner,
    private val uiManager: GameUIManager
) {
    companion object {
        private const val TAG = "AnimationManager"
    }

    private var heartAnimator: ValueAnimator? = null
    private var characterImageAnimator: ValueAnimator? = null
    private var particleAnimationJob: kotlinx.coroutines.Job? = null

    fun updateStatusDisplay(affinity: Int) {
        heartAnimator?.cancel()
        uiManager.ivHeartIcon.clearAnimation()

        // Update heart icon size based on affinity
        val scale = when {
            affinity < 0 -> 0.6f + (kotlin.math.max(affinity, -50) + 50) / 50f * 0.2f
            else -> 0.8f + (affinity / 100f) * 0.4f
        }
        uiManager.ivHeartIcon.scaleX = scale
        uiManager.ivHeartIcon.scaleY = scale

        // Update heart icon color based on affinity
        val colorMatrix = ColorMatrix()
        when {
            affinity < 0 -> {
                colorMatrix.setSaturation(0f)
                val darkness = kotlin.math.max(affinity, -50) / -50f
                colorMatrix.postConcat(ColorMatrix(floatArrayOf(
                    0.3f, 0.3f, 0.3f, 0f, 0f,
                    0.3f, 0.3f, 0.3f, 0f, 0f,
                    0.3f, 0.3f, 0.3f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
                uiManager.ivHeartIcon.alpha = 0.7f + darkness * 0.3f
            }
            affinity == 0 -> {
                colorMatrix.setSaturation(0.2f)
                uiManager.ivHeartIcon.alpha = 0.8f
            }
            else -> {
                val saturation = affinity / 100f
                colorMatrix.setSaturation(saturation)
                uiManager.ivHeartIcon.alpha = 1.0f
            }
        }
        val filter = ColorMatrixColorFilter(colorMatrix)
        uiManager.ivHeartIcon.colorFilter = filter

        if (affinity > 60) {
            val beatDuration = (1500 - 10 * affinity).toLong().coerceIn(500, 1500)
            val beatIntensity = 1.0f + (affinity / 100f) * 0.25f

            heartAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = beatDuration
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val pulse = 1f + (kotlin.math.sin((it.animatedValue as Float) * Math.PI) * (beatIntensity - 1f)).toFloat()
                    uiManager.ivHeartIcon.scaleX = scale * pulse
                    uiManager.ivHeartIcon.scaleY = scale * pulse
                }
                start()
            }
        }

        uiManager.updateConfessButtonVisibility()
    }

    fun updateCharacterImage(imagePath: String?) {
        imagePath?.let { path ->
            val characterName = "megumi" // Fixed character name
            val imageName = path.substringAfterLast('/')
            val correctPath = "images/$characterName/$imageName"

            try {
                activity.assets.open(correctPath).use { inputStream ->
                    val drawable = Drawable.createFromStream(inputStream, null)
                    
                    drawable?.let {
                        // 메인 캐릭터 이미지 설정
                        uiManager.ivCharacter.setImageDrawable(it)
                        
                        // 블러 배경 이미지 설정
                        setBlurredBackground(it)
                        
                        Log.d(TAG, "Image loaded successfully from: $correctPath")
                    } ?: Log.e(TAG, "Failed to create drawable from stream: $correctPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image from assets: $correctPath", e)
                try {
                    val fallbackImageName = "${characterName}_lab_normal.png"
                    val fallbackPath = "images/$characterName/$fallbackImageName"
                    activity.assets.open(fallbackPath).use { inputStream ->
                        val drawable = Drawable.createFromStream(inputStream, null)
                        
                        drawable?.let {
                            uiManager.ivCharacter.setImageDrawable(it)
                            setBlurredBackground(it)
                            Log.d(TAG, "Fallback image loaded successfully from: $fallbackPath")
                        } ?: Log.e(TAG, "Failed to create fallback drawable from stream: $fallbackPath")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading default image from assets.", e)
                }
            }
        }
    }

    private fun setBlurredBackground(drawable: Drawable) {
        try {
            val originalBitmap = drawableToBitmap(drawable)
            val extendedImage = createNaturalExtendedImage(originalBitmap)
            val extendedDrawable = BitmapDrawable(activity.resources, extendedImage)
            uiManager.ivCharacter.setImageDrawable(extendedDrawable)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating blurred background", e)
            uiManager.ivCharacter.setImageDrawable(drawable)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createNaturalExtendedImage(originalBitmap: Bitmap): Bitmap {
        val containerWidth = uiManager.ivCharacter.width
        val containerHeight = uiManager.ivCharacter.height
        
        if (containerWidth <= 0 || containerHeight <= 0) {
            return originalBitmap
        }
        
        val widthScale = containerWidth.toFloat() / originalBitmap.width
        val heightScale = containerHeight.toFloat() / originalBitmap.height
        val scale = kotlin.math.max(widthScale, heightScale)
        
        val scaledWidth = (originalBitmap.width * scale).toInt()
        val scaledHeight = (originalBitmap.height * scale).toInt()
        
        val result = Bitmap.createBitmap(containerWidth, containerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val centerLeft = (containerWidth - scaledWidth) / 2
        val centerTop = (containerHeight - scaledHeight) / 2
        
        val blurWidth = (containerWidth * 0.2f).toInt()
        val centerImageWidth = containerWidth - (blurWidth * 2)
        
        // 1. 전체 배경을 블러 처리된 이미지로 채움
        val backgroundBlurred = blurBitmap(originalBitmap)
        val backgroundScaled = Bitmap.createScaledBitmap(backgroundBlurred, containerWidth, containerHeight, true)
        canvas.drawBitmap(backgroundScaled, 0f, 0f, null)
        
        // 2. 중앙 60% 영역에 선명한 이미지 배치
        val centerScale = kotlin.math.max(
            centerImageWidth.toFloat() / originalBitmap.width,
            containerHeight.toFloat() / originalBitmap.height
        )
        
        val centerScaledWidth = (originalBitmap.width * centerScale).toInt()
        val centerScaledHeight = (originalBitmap.height * centerScale).toInt()
        val centerScaled = Bitmap.createScaledBitmap(originalBitmap, centerScaledWidth, centerScaledHeight, true)
        
        val centerImageLeft = blurWidth + (centerImageWidth - centerScaledWidth) / 2
        val centerImageTop = (containerHeight - centerScaledHeight) / 2
        
        canvas.drawBitmap(centerScaled, centerImageLeft.toFloat(), centerImageTop.toFloat(), null)
        
        // 3. 양옆 블러 영역에 그라데이션 적용
        val leftGradient = Paint().apply {
            shader = LinearGradient(
                0f, 0f, blurWidth.toFloat(), 0f,
                intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, 0f, blurWidth.toFloat(), containerHeight.toFloat(), leftGradient)
        
        val rightGradient = Paint().apply {
            shader = LinearGradient(
                (containerWidth - blurWidth).toFloat(), 0f, containerWidth.toFloat(), 0f,
                intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect((containerWidth - blurWidth).toFloat(), 0f, containerWidth.toFloat(), containerHeight.toFloat(), rightGradient)
        
        return result
    }

    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 12, bitmap.height / 12, false)
        return Bitmap.createScaledBitmap(smallBitmap, bitmap.width, bitmap.height, true)
    }

    /**
     * SLM 추론 지연을 숨기기 위한 시각적 효과들 시작
     */
    fun startDelayDistractionEffects() {
        startCharacterThinkingAnimation()
        startHeartPulseEffect()
        showThinkingTextOverlay()
    }
    
    /**
     * 지연 숨김 효과들 종료
     */
    fun stopDelayDistractionEffects() {
        particleAnimationJob?.cancel()
        characterImageAnimator?.cancel()
        
        uiManager.ivCharacter.scaleX = 1.0f
        uiManager.ivCharacter.scaleY = 1.0f
        
        hideThinkingTextOverlay()
    }
    
    /**
     * 캐릭터 이미지 고민 애니메이션
     */
    private fun startCharacterThinkingAnimation() {
        characterImageAnimator = ValueAnimator.ofFloat(1.0f, 0.85f, 1.0f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                uiManager.ivCharacter.scaleX = scale
                uiManager.ivCharacter.scaleY = scale
            }
            start()
        }
    }
    
    /**
     * 하트 아이콘 미묘한 펄스 효과
     */
    private fun startHeartPulseEffect() {
        uiManager.ivHeartIcon.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(600)
            .withEndAction {
                uiManager.ivHeartIcon.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(600)
                    .start()
            }
            .start()
    }
    
    /**
     * 캐릭터 이미지에 "사고중" 텍스트 오버레이 표시 (다국어 지원)
     */
    private fun showThinkingTextOverlay() {
        val baseText = LanguageManager.getText(activity, "thinking").removeSuffix("…").removeSuffix("...")
        uiManager.tvThinkingOverlay.text = baseText
        uiManager.tvThinkingOverlay.visibility = View.VISIBLE
        
        uiManager.tvThinkingOverlay.alpha = 0f
        uiManager.tvThinkingOverlay.animate()
            .alpha(1f)
            .setDuration(500)
            .withEndAction {
                lifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    simulateThinkingAnimation()
                }
            }
            .start()
    }
    
    /**
     * "思考中…" 텍스트 오버레이 숨김
     */
    private fun hideThinkingTextOverlay() {
        uiManager.tvThinkingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                uiManager.tvThinkingOverlay.visibility = View.GONE
            }
            .start()
    }

    /**
     * "생각하는 중..." 타이핑 시뮬레이션 - 캐릭터 오버레이에 표시 (다국어 지원)
     */
    private suspend fun simulateThinkingAnimation() {
        val baseText = LanguageManager.getText(activity, "thinking").removeSuffix("…").removeSuffix("...")
        val language = LanguageManager.getLanguage(activity)
        
        val thinkingMessages = if (language == "en") {
            listOf(
                "$baseText.",
                "$baseText..",
                "$baseText...",
                "Megumi is thinking...",
                "How should I respond...",
                "What should I say...",
                "Hmm...",
                "Let me think...",
                "$baseText."
            )
        } else {
            listOf(
                "$baseText.",
                "$baseText..",
                "$baseText...",
                "めぐみは考えている...",
                "どう答えようかな...",
                "悩んでいる...",
                "考え中...",
                "どうしよう...",
                "$baseText."
            )
        }
        
        // 오버레이가 표시되어 있는 동안 무한 반복
        while (uiManager.tvThinkingOverlay.visibility == View.VISIBLE) {
            for (message in thinkingMessages) {
                uiManager.tvThinkingOverlay.text = message
                delay(2500) // 2.5초 딜레이
                
                if (uiManager.tvThinkingOverlay.visibility != View.VISIBLE) {
                    return
                }
            }
        }
    }

    fun onPause() {
        heartAnimator?.cancel()
        characterImageAnimator?.cancel()
        particleAnimationJob?.cancel()
    }

    fun startAffinityChangeAnimation(isPositive: Boolean, particleView: ParticleView) {
        if (isPositive) {
            particleView.startAnimation(ParticleView.ParticleType.HEART)
        } else {
            particleView.startAnimation(ParticleView.ParticleType.SAD)
        }
    }
    
    /**
     * 고백 판정 시 극적인 heartbeat 애니메이션 시작
     */
    fun startCharacterHeartbeatAnimation() {
        Log.d(TAG, "Starting dramatic heartbeat animation")
        
        characterImageAnimator?.cancel()
        characterImageAnimator = ValueAnimator.ofFloat(1.0f, 0.9f, 1.1f, 1.0f).apply {
            duration = 800 // 더 빠른 heartbeat
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                uiManager.ivCharacter.scaleX = scale
                uiManager.ivCharacter.scaleY = scale
            }
            start()
        }
    }
    
    /**
     * 고백 판정 heartbeat 애니메이션 중지
     */
    fun stopCharacterHeartbeatAnimation() {
        Log.d(TAG, "Stopping dramatic heartbeat animation")
        characterImageAnimator?.cancel()
        
        // 원래 크기로 복원
        uiManager.ivCharacter.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .start()
    }
    
    /**
     * 극적인 두근거림 파티클 효과 시작
     */
    fun startHeartbeatParticleEffect(particleView: ParticleView) {
        Log.d(TAG, "Starting heartbeat particle effect")
        
        particleAnimationJob?.cancel()
        particleAnimationJob = lifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                particleView.startAnimation(ParticleView.ParticleType.HEART)
                delay(1000) // 1초마다 하트 파티클 발생
            }
        }
    }
    
    /**
     * 두근거림 파티클 효과 중지
     */
    fun stopHeartbeatParticleEffect(particleView: ParticleView) {
        Log.d(TAG, "Stopping heartbeat particle effect")
        particleAnimationJob?.cancel()
        particleView.stopAnimation()
    }
}