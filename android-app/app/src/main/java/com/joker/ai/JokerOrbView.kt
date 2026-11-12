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

    // 1700 Dense Micro-Particles (Video 1 smooth density)
    private val pointCount = 1700
    private val basePoints = ArrayList<FloatArray>(pointCount)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private var angleX = 0f
    private var angleY = 0f
    private var time = 0f

    var currentState: OrbState = OrbState.IDLE

    init {
        // Fibonacci Spiral Algorithm for a pure spherical surface
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
        
        // Dynamic size: Keeps the orb large and majestic
        val baseRadius = min(centerX, centerY) * 0.78f

        // SLOW, CINEMATIC, SMOOTH SPEEDS (Video 1 exact motion)
        val (speedY, waveSpeed, waveIntensity, breatheAmp) = when (currentState) {
            OrbState.IDLE -> arrayOf(0.0035f, 0.025f, 0.045f, 0.03f)
            OrbState.LISTENING -> arrayOf(0.006f, 0.06f, 0.07f, 0.09f) // Subtle expansion
            OrbState.THINKING -> arrayOf(0.010f, 0.05f, 0.06f, 0.05f)
            OrbState.SPEAKING -> arrayOf(0.005f, 0.08f, 0.085f, 0.07f) // Acoustic wave shimmer (stays round!)
        }

        angleY += speedY as Float
        angleX += (speedY * 0.3f)
        time += waveSpeed as Float

        val cosX = cos(angleX)
        val sinX = sin(angleX)
        val cosY = cos(angleY)
        val sinY = sin(angleY)

        val intensity = waveIntensity as Float
        val breathe = sin(time * 0.8f) * (breatheAmp as Float)
        val effectiveBaseRadius = baseRadius * (1f + breathe)

        // Outer Glowing Red Halo Ring (Video 1 circular border)
        val haloColor = when (currentState) {
            OrbState.LISTENING -> Color.parseColor("#00FF66")
            OrbState.THINKING -> Color.parseColor("#FF9900")
            else -> Color.parseColor("#FF0033")
        }
        haloPaint.color = Color.argb(45, Color.red(haloColor), Color.green(haloColor), Color.blue(haloColor))
        canvas.drawCircle(centerX, centerY, effectiveBaseRadius * 1.10f, haloPaint)

        // Red Cyberpunk Palette
        val (baseR, baseG, baseB) = when (currentState) {
            OrbState.LISTENING -> arrayOf(0, 255, 102)
            OrbState.THINKING -> arrayOf(255, 153, 0)
            else -> arrayOf(255, 20, 50) // Vibrant Red Theme
        }

        // Render each 3D particle: Stays strictly round with subtle liquid shimmers
        for (i in 0 until pointCount) {
            val bp = basePoints[i]
            val bx = bp[0]
            val by = bp[1]
            val bz = bp[2]

            // Video 1 Gentle Surface Ripple (Caps distortion to keep round sphere intact)
            val ripple = sin(bx * 3.5f + time) * cos(by * 3.5f - time * 0.7f) * 0.6f +
                         sin(bz * 4.0f + time * 1.2f) * 0.4f
            val currentR = effectiveBaseRadius * (1f + ripple * intensity)

            // 3D Rotation Matrix
            val x1 = bx * cosY + bz * sinY
            val z1 = -bx * sinY + bz * cosY

            val y2 = by * cosX - z1 * sinX
            val z2 = by * sinX + z1 * cosX

            // Perspective Projection
            val perspective = (z2 + 2.4f) / 3.4f
            val screenX = centerX + x1 * currentR * perspective
            val screenY = centerY + y2 * currentR * perspective

            // Depth Shading: Front particles shine brighter and larger
            val depthRatio = max(0f, min(1f, (z2 + 1f) / 2f))
            val alpha = (40 + (depthRatio * 215)).toInt()
            val dotRadius = max(1.0f, 0.9f + depthRatio * 2.3f)

            // White-hot core highlights on the front face
            val finalR = min(255, (baseR + (255 - baseR) * depthRatio * 0.7f).toInt())
            val finalG = min(255, (baseG + (255 - baseG) * depthRatio * 0.85f).toInt())
            val finalB = min(255, (baseB + (255 - baseB) * depthRatio * 0.85f).toInt())

            dotPaint.color = Color.argb(alpha, finalR, finalG, finalB)
            canvas.drawCircle(screenX, screenY, dotRadius, dotPaint)
        }

        // Buttery-smooth rendering
        postInvalidateOnAnimation()
    }

    fun setState(state: OrbState) {
        currentState = state
        postInvalidateOnAnimation()
    }
}
