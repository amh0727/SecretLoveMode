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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG) // 부드러운 모양을 위해 ANTI_ALIAS 플래그 추가
    private var isAnimating = false
    private var particleType: ParticleType = ParticleType.NONE
    private var animationDuration = 2000L // 애니메이션 지속 시간 (2초)
    private var animationStartTime = 0L

    /**
     * 1. ParticleType을 public으로 변경하여 GameActivity 같은 외부 클래스에서 접근할 수 있도록 합니다.
     * 2. HEART와 SAD 타입을 추가합니다.
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
     * 3. 외부에서 애니메이션을 시작할 수 있는 public 함수를 만듭니다.
     *    GameActivity에서 이 함수를 호출하게 됩니다.
     */
    fun startAnimation(type: ParticleType) {
        // 애니메이션이 이미 실행 중이면 중복 실행 방지
        if (isAnimating) return

        this.particleType = type
        particles.clear() // 이전 파티클 제거

        // 뷰의 크기가 정해졌을 때만 파티클 생성
        if (width > 0 && height > 0) {
            val particleCount = when (type) {
                ParticleType.HEART, ParticleType.SAD -> 30 // 하트/슬픔은 개수를 조금 적게
                else -> 50
            }
            for (i in 0 until particleCount) {
                addParticle()
            }
        }
        animationStartTime = System.currentTimeMillis()
        isAnimating = true
        postInvalidateOnAnimation() // 애니메이션 시작
    }

    fun stopAnimation() {
        isAnimating = false
        particles.clear()
        invalidate()
    }

    private fun addParticle() {
        // 파티클이 화면 아래에서 위로 올라가도록 시작 위치와 속도 조절
        val x = Random.nextFloat() * width
        val y = height + Random.nextFloat() * 100 // 화면 바로 아래에서 시작
        val speedY = -(Random.nextFloat() * 3 + 2) // 위로 올라가는 속도
        val speedX = Random.nextFloat() * 4 - 2 // 좌우로 흔들리는 속도
        val size = Random.nextFloat() * 20 + 15 // 파티클 크기
        particles.add(Particle(x, y, speedY, speedX, size))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isAnimating) return

        // 애니메이션 시간이 다 되면 종료
        val elapsedTime = System.currentTimeMillis() - animationStartTime
        if (elapsedTime > animationDuration) {
            stopAnimation()
            return
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()

            // 위치 업데이트
            particle.y += particle.speedY
            particle.x += particle.speedX
            particle.rotation += particle.rotationSpeed

            // 시간이 지나면서 서서히 투명해지도록 알파값 조절
            particle.alpha = (255 * (1f - elapsedTime.toFloat() / animationDuration)).toInt().coerceIn(0, 255)

            // 화면 밖으로 나가거나 완전히 투명해지면 제거
            if (particle.y < -particle.size || particle.alpha <= 0) {
                iterator.remove()
                continue
            }

            paint.alpha = particle.alpha
            canvas.save()
            canvas.translate(particle.x, particle.y)
            canvas.rotate(particle.rotation)

            // 4. HEART와 SAD 타입에 대한 그리기 로직 추가
            when (particleType) {
                ParticleType.HEART -> {
                    paint.color = Color.parseColor("#FF4081") // 핑크색
                    drawHeart(canvas, particle.size)
                }
                ParticleType.SAD -> {
                    paint.color = Color.parseColor("#536DFE") // 파란색
                    drawTear(canvas, particle.size)
                }
                // 기존 파티클 효과들은 유지
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
                ParticleType.NONE -> { /* 아무것도 안 함 */ }
            }
            canvas.restore()
        }

        // 애니메이션이 진행 중이면 다음 프레임 요청
        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }

    // 하트 모양을 그리는 함수
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

    // 눈물(물방울) 모양을 그리는 함수
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