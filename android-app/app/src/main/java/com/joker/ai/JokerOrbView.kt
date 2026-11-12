package com.joker.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class JokerOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 900 Particles (Exact aapke code jaisa)
    private val particleCount = 900
    private val particles = ArrayList<FloatArray>(particleCount)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private var phase = 0f
    var currentState: OrbState = OrbState.IDLE

    init {
        val random = Random(42)
        val phi = Math.PI * (1.0 + sqrt(5.0))
        for (i in 0 until particleCount) {
            val y = 1.0 - 2.0 * (i + 0.5) / particleCount
            val radius = sqrt(max(0.0, 1.0 - y * y))
            val theta = phi * i

            val x = (cos(theta) * radius).toFloat()
            val z = (sin(theta) * radius).toFloat()
            val size = 0.7f + random.nextFloat() * 1.2f
            val twinkle = random.nextFloat()

            particles.add(floatArrayOf(x, y.toFloat(), z, size, twinkle))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val orbRadius = min(centerX, centerY) * 0.76f

        // Exact Behaviour: Chup rehne par STILL (motion = 0)
        val isMoving = (currentState == OrbState.LISTENING || 
                        currentState == OrbState.SPEAKING || 
                        currentState == OrbState.THINKING)

        val motion = if (isMoving) 1f else 0f

        // Bolne par hi phase aage badhega, silence mein freeze ho jayega
        if (isMoving) {
            val speed = when (currentState) {
                OrbState.SPEAKING -> 0.045f
                OrbState.LISTENING -> 0.035f
                OrbState.THINKING -> 0.055f
                else -> 0f
            }
            phase += speed
        }

        val rotation = phase
        val pulse = if (isMoving) 1f + 0.035f * sin(phase * 2.2f) * motion else 1f

        val cosR = cos(rotation)
        val sinR = sin(rotation)

        // 900 Particles render
        for (p in particles) {
            val x = p[0]
            val y = p[1]
            val z = p[2]
            val pSize = p[3]
            val twinkle = p[4]

            val ripple = if (isMoving) {
                sin(phase * 2.0f + twinkle * 6.28f) * 0.035f * motion
            } else 0f

            val x0 = x * (1f + ripple)
            val z0 = z * (1f + ripple)

            val rotatedX = x0 * cosR - z0 * sinR
            val rotatedZ = x0 * sinR + z0 * cosR

            // 3D Perspective
            val perspective = 1f / (1f + rotatedZ * 0.22f)
            val px = centerX + rotatedX * orbRadius * pulse * perspective
            val py = centerY + y * orbRadius * pulse * perspective

            val frontness = max(0f, min(1f, (rotatedZ + 1f) / 2f))
            val glow = if (isMoving) {
                max(0.35f, min(1f, 0.55f + frontness * 0.35f + motion * 0.35f))
            } else {
                0.72f + frontness * 0.22f
            }

            // RED THEME: Deep Red with White-Hot Highlights in front
            val finalR = min(255, ((0.95f + frontness * 0.05f) * glow * 255).toInt())
            val finalG = min(255, ((0.08f + frontness * 0.32f) * glow * 255).toInt())
            val finalB = min(255, ((0.15f + frontness * 0.32f) * glow * 255).toInt())
            val alpha = min(255, ((0.35f + frontness * 0.65f) * 255).toInt())

            dotPaint.color = Color.argb(alpha, finalR, finalG, finalB)
            val dotRadius = pSize * (0.75f + frontness * 0.55f) * (if (isMoving) 1f + motion * 0.7f else 1f)

            canvas.drawCircle(px, py, dotRadius, dotPaint)
        }

        // Outer Red Pulse Ring (Bolte waqt chalegi)
        if (isMoving) {
            pulsePaint.color = Color.argb((40 + motion * 40).toInt(), 255, 0, 51)
            canvas.drawCircle(centerX, centerY, orbRadius * pulse * 1.08f, pulsePaint)
        }

        // Always running loop
        postInvalidateOnAnimation()
    }

    fun setState(state: OrbState) {
        currentState = state
        postInvalidateOnAnimation()
    }
}
