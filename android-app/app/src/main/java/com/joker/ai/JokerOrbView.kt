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

    private val pointCount = 450 // 450 3D particle dots
    private val points = ArrayList<FloatArray>(pointCount)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var angleX = 0f
    private var angleY = 0f
    private var pulsePhase = 0f

    var currentState: OrbState = OrbState.IDLE

    init {
        // Fibonacci Sphere Algorithm for uniform particle distribution
        val phi = Math.PI * (3.0 - sqrt(5.0)) // Golden angle
        for (i in 0 until pointCount) {
            val y = 1.0 - (i.toDouble() / (pointCount - 1)) * 2.0
            val radiusAtY = sqrt(max(0.0, 1.0 - y * y))
            val theta = phi * i

            val x = cos(theta) * radiusAtY
            val z = sin(theta) * radiusAtY

            points.add(floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat()))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = min(centerX, centerY) * 0.72f

        // State-based dynamic speeds and pulse
        val (speedX, speedY, pulseSpeed, pulseAmplitude, baseColor) = when (currentState) {
            OrbState.IDLE -> arrayOf(0.005f, 0.009f, 0.04f, 0.05f, Color.parseColor("#FF1E44"))
            OrbState.LISTENING -> arrayOf(0.015f, 0.025f, 0.12f, 0.18f, Color.parseColor("#00FF66")) // Listening Green
            OrbState.THINKING -> arrayOf(0.035f, 0.045f, 0.08f, 0.12f, Color.parseColor("#FF9900"))  // Thinking Orange
            OrbState.SPEAKING -> arrayOf(0.012f, 0.018f, 0.18f, 0.22f, Color.parseColor("#FF1E44"))  // Energetic Red
        }

        angleX += speedX as Float
        angleY += speedY as Float
        pulsePhase += pulseSpeed as Float

        val dynamicRadius = baseRadius * (1f + sin(pulsePhase) * (pulseAmplitude as Float))

        val cosX = cos(angleX)
        val sinX = sin(angleX)
        val cosY = cos(angleY)
        val sinY = sin(angleY)

        val col = baseColor as Int
        val red = Color.red(col)
        val green = Color.green(col)
        val blue = Color.blue(col)

        // Project 3D points to 2D Screen
        for (p in points) {
            val x = p[0]
            val y = p[1]
            val z = p[2]

            // Rotate around Y-axis
            val x1 = x * cosY + z * sinY
            val z1 = -x * sinY + z * cosY

            // Rotate around X-axis
            val y2 = y * cosX - z1 * sinX
            val z2 = y * sinX + z1 * cosX

            // Perspective scale (z2 in [-1, 1])
            val perspective = (z2 + 1.8f) / 2.8f
            val screenX = centerX + x1 * dynamicRadius * perspective
            val screenY = centerY + y2 * dynamicRadius * perspective

            // Depth Shading: Front particles are larger and brighter
            val alpha = (max(20f, min(255f, (z2 + 1.2f) * 110f))).toInt()
            val dotSize = max(1.5f, (z2 + 1.2f) * 2.2f)

            dotPaint.color = Color.argb(alpha, red, green, blue)
            canvas.drawCircle(screenX, screenY, dotSize, dotPaint)
        }

        // 60-120 FPS continuous buttery-smooth render loop
        postInvalidateOnAnimation()
    }

    fun setState(state: OrbState) {
        currentState = state
        postInvalidateOnAnimation()
    }
}
