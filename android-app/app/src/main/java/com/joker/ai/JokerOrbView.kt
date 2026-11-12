package com.joker.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

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

    // 1600 Dense Organic Particles (Video 1 density)
    private val pointCount = 1600
    private val basePoints = ArrayList<FloatArray>(pointCount)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var angleX = 0f
    private var angleY = 0f
    private var time = 0f

    var currentState: OrbState = OrbState.IDLE

    init {
        // Fibonacci Spiral Algorithm for organic spherical particle cloud
        val phi = Math.PI * (3.0 - sqrt(5.0))
        for (i in 0 until pointCount) {
            val y = 1.0 - (i.toDouble() / (pointCount - 1)) * 2.0
            val radiusAtY = sqrt(max(0.0, 1.0 - y * y))
            val theta = phi * i

            val x = cos(theta) * radiusAtY
            val z = sin(theta) * radiusAtY

            basePoints.add(floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat()))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(centerX, centerY) * 0.72f

        // Video 1 Wave Dynamics & Fluid Speeds
        val (speedY, waveFreq, waveSpeed, waveIntensity) = when (currentState) {
            OrbState.IDLE -> arrayOf(0.007f, 2.8f, 0.04f, 0.18f)
            OrbState.LISTENING -> arrayOf(0.022f, 4.2f, 0.09f, 0.32f) // Reactive expansion
            OrbState.THINKING -> arrayOf(0.035f, 5.5f, 0.07f, 0.22f)  // Energetic swirl
            OrbState.SPEAKING -> arrayOf(0.016f, 3.8f, 0.15f, 0.38f)  // Audio reactive waves
        }

        angleY += speedY as Float
        angleX += (speedY * 0.35f)
        time += waveSpeed as Float

        val cosX = cos(angleX)
        val sinX = sin(angleX)
        val cosY = cos(angleY)
        val sinY = sin(angleY)

        val freq = waveFreq as Float
        val intensity = waveIntensity as Float

        // Outer Ethereal Glowing Aura Ring (Video 1 background ring)
        val auraColor = when (currentState) {
            OrbState.LISTENING -> Color.parseColor("#00FF66")
            OrbState.THINKING -> Color.parseColor("#FF9900")
            else -> Color.parseColor("#FF0033") // Red Theme
        }

        auraPaint.color = Color.argb(35, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor))
        canvas.drawCircle(centerX, centerY, baseRadius * 1.15f, auraPaint)
        auraPaint.color = Color.argb(18, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor))
        canvas.drawCircle(centerX, centerY, baseRadius * 1.25f, auraPaint)

        // Red Cyberpunk Palette
        val (baseR, baseG, baseB) = when (currentState) {
            OrbState.LISTENING -> arrayOf(0, 255, 102) // Green while listening
            OrbState.THINKING -> arrayOf(255, 153, 0)   // Orange while thinking
            else -> arrayOf(255, 20, 50)               // Signature Red Theme
        }

        // Render each particle with 3D multi-octave fluid wave displacement
        for (i in 0 until pointCount) {
            val bp = basePoints[i]
            val bx = bp[0]
            val by = bp[1]
            val bz = bp[2]

            // Video 1 Wave Equation: Multi-frequency surface turbulence
            val wave1 = sin(bx * freq + time) * cos(by * freq - time * 0.7f)
            val wave2 = sin(bz * (freq * 1.3f) + time * 1.2f) * 0.5f
            val wave3 = cos((bx + by) * 2f - time * 1.5f) * 0.3f
            val totalWave = (wave1 + wave2 + wave3)

            val currentR = baseRadius * (1f + totalWave * intensity)

            // 3D Matrix Rotation (Y then X)
            val x1 = bx * cosY + bz * sinY
            val z1 = -bx * sinY + bz * cosY

            val y2 = by * cosX - z1 * sinX
            val z2 = by * sinX + z1 * cosX

            // Perspective Projection
            val perspective = (z2 + 2.4f) / 3.4f
            val screenX = centerX + x1 * currentR * perspective
            val screenY = centerY + y2 * currentR * perspective

            // Depth Lighting: Front particles glow bright white-hot red
            val depthRatio = max(0f, min(1f, (z2 + 1f) / 2f))
            val alpha = (35 + (depthRatio * 220)).toInt()
            val dotRadius = max(1.0f, 0.8f + depthRatio * 2.6f)

            // Core highlights turn brilliant white in the foreground
            val finalR = min(255, (baseR + (255 - baseR) * depthRatio * 0.7f).toInt())
            val finalG = min(255, (baseG + (255 - baseG) * depthRatio * 0.85f).toInt())
            val finalB = min(255, (baseB + (255 - baseB) * depthRatio * 0.85f).toInt())

            dotPaint.color = Color.argb(alpha, finalR, finalG, finalB)
            canvas.drawCircle(screenX, screenY, dotRadius, dotPaint)
        }

        // 60-120 FPS continuous loop
        postInvalidateOnAnimation()
    }

    fun setState(state: OrbState) {
        currentState = state
        postInvalidateOnAnimation()
    }
}
