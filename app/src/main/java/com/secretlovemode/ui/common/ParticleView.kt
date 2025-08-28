package com.secretlovemode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG) // Add ANTI_ALIAS flag for smooth shapes
    private var isAnimating = false
    private var particleType: ParticleType = ParticleType.NONE
    private var animationDuration = 2000L // Animation duration (2 seconds)
    private var animationStartTime = 0L

    /**
     * 1. Make ParticleType public so it can be accessed from external classes like GameActivity.
     * 2. Add HEART and SAD types.
     */
    enum class ParticleType {
        NONE, HEART, SAD, SNOW, LEAVES, CHERRY_BLOSSOMS
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var speedY: Float,
        var speedX: Float,
        var size: Float,
        var alpha: Int = 255,
        var rotation: Float = 0f,
        var rotationSpeed: Float = Random.nextFloat() * 4 - 2
    )

    /**
     * 3. Create a public function to start animation from outside.
     *    GameActivity will call this function.
     */
    fun startAnimation(type: ParticleType) {
        // Prevent duplicate execution if animation is already running
        if (isAnimating) return

        this.particleType = type
        particles.clear() // Remove previous particles

        // Generate particles only when view size is determined
        if (width > 0 && height > 0) {
            val particleCount = when (type) {
                ParticleType.HEART, ParticleType.SAD -> 60 // More particles for better effect
                else -> 80
            }
            for (i in 0 until particleCount) {
                addParticle()
            }
        }
        animationStartTime = System.currentTimeMillis()
        isAnimating = true
        postInvalidateOnAnimation() // Start animation
    }

    fun stopAnimation() {
        isAnimating = false
        particles.clear()
        invalidate()
    }

    private fun addParticle() {
        // Create particles across the screen width and various starting positions
        val x = Random.nextFloat() * width
        val startFromTop = Random.nextBoolean()
        val y = if (startFromTop) {
            -Random.nextFloat() * 200 // Start above screen
        } else {
            height + Random.nextFloat() * 200 // Start below screen
        }
        val speedY = if (startFromTop) {
            Random.nextFloat() * 4 + 2 // Downward speed
        } else {
            -(Random.nextFloat() * 4 + 2) // Upward speed
        }
        val speedX = Random.nextFloat() * 6 - 3 // More sideways movement
        val size = Random.nextFloat() * 30 + 20 // Larger particles
        particles.add(Particle(x, y, speedY, speedX, size))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isAnimating) return

        // End animation when time is up
        val elapsedTime = System.currentTimeMillis() - animationStartTime
        if (elapsedTime > animationDuration) {
            stopAnimation()
            return
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()

            // Update position
            particle.y += particle.speedY
            particle.x += particle.speedX
            particle.rotation += particle.rotationSpeed

            // Adjust alpha to fade out gradually over time
            particle.alpha = (255 * (1f - elapsedTime.toFloat() / animationDuration)).toInt().coerceIn(0, 255)

            // Remove if off screen or fully transparent
            if (particle.y < -particle.size * 2 || particle.y > height + particle.size * 2 || 
                particle.x < -particle.size * 2 || particle.x > width + particle.size * 2 || 
                particle.alpha <= 0) {
                iterator.remove()
                continue
            }

            paint.alpha = particle.alpha
            canvas.save()
            canvas.translate(particle.x, particle.y)
            canvas.rotate(particle.rotation)

            // 4. Add drawing logic for HEART and SAD types
            when (particleType) {
                ParticleType.HEART -> {
                    paint.color = Color.parseColor("#FF4081") // Pink
                    drawHeart(canvas, particle.size)
                }
                ParticleType.SAD -> {
                    paint.color = Color.parseColor("#536DFE") // Blue
                    drawTear(canvas, particle.size)
                }
                // Existing particle effects are maintained
                ParticleType.SNOW -> {
                    paint.color = Color.WHITE
                    canvas.drawCircle(0f, 0f, particle.size / 2, paint)
                }
                ParticleType.LEAVES -> {
                    paint.color = Color.parseColor("#B8860B")
                    canvas.drawRect(-particle.size / 2, -particle.size / 4, particle.size / 2, particle.size / 4, paint)
                }
                ParticleType.CHERRY_BLOSSOMS -> {
                    paint.color = Color.parseColor("#FFB6C1")
                    canvas.drawOval(-particle.size / 2, -particle.size / 4, particle.size / 2, particle.size / 4, paint)
                }
                ParticleType.NONE -> { /* Do nothing */ }
            }
            canvas.restore()
        }

        // Request next frame if animation is ongoing
        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }

    // Function to draw a heart shape
    private fun drawHeart(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.FILL
        val path = Path()
        val halfSize = size / 2f
        val quarterSize = size / 4f
        path.moveTo(0f, halfSize - quarterSize) // Bottom point
        path.cubicTo(-size, -quarterSize, -halfSize, -halfSize, 0f, -quarterSize) // Left side
        path.cubicTo(halfSize, -halfSize, size, -quarterSize, 0f, halfSize - quarterSize) // Right side
        path.close()
        canvas.drawPath(path, paint)
    }

    // Function to draw a tear (water drop) shape
    private fun drawTear(canvas: Canvas, size: Float) {
        paint.style = Paint.Style.FILL
        val path = Path()
        val halfSize = size / 2f
        path.moveTo(0f, -halfSize) // Top point
        path.quadTo(halfSize, 0f, 0f, halfSize) // Right side
        path.quadTo(-halfSize, 0f, 0f, -halfSize) // Left side
        path.close()
        canvas.drawPath(path, paint)
    }
}