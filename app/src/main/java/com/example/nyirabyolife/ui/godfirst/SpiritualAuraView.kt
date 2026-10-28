package com.example.nyirabyolife.ui.godfirst

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * SpiritualAuraView: A dynamic spiritual animation component ("Spiritual Anime")
 * representing the divine light of God the Father and His Son Jesus Christ.
 *
 * Features:
 * - Dynamic breathing golden halo & divine rays
 * - Rising golden embers/particles representing prayers rising to Heaven
 * - Interactive Holy Breath meditation cycle ("The Jesus Prayer" & "Be Still")
 */
class SpiritualAuraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Breathing animation state: 0f to 1f
    private var breathProgress = 0f
    private var rotationAngle = 0f
    private var isMeditationActive = true

    // Breathing guidance states
    enum class BreathPhase(val text: String, val subText: String) {
        INHALE("INHALE", "“Lord Jesus Christ, Son of God…”"),
        HOLD("ABIDE", "“Rest in the Father's unchanging love…”"),
        EXHALE("EXHALE", "“Fill my soul with Your Holy Spirit…”"),
        STILL("BE STILL", "“Be still, and know that I am God.” (Ps 46:10)")
    }

    private var currentPhase = BreathPhase.INHALE
    var onPhaseChangeListener: ((BreathPhase) -> Unit)? = null

    // Golden Palette
    private val goldPrimary = Color.parseColor("#E5B869")
    private val goldBright = Color.parseColor("#FFE8A3")
    private val goldAura = Color.parseColor("#66E5B869")
    private val emeraldAccent = Color.parseColor("#10B981")

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = goldBright
        style = Paint.Style.FILL
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Floating Embers
    private data class Ember(
        var x: Float,
        var y: Float,
        var speedY: Float,
        var radius: Float,
        var alpha: Int,
        var maxAlpha: Int
    )

    private val embers = ArrayList<Ember>()
    private val emberCount = 18

    private var animator: ValueAnimator? = null

    init {
        setupAnimator()
    }

    private fun setupAnimator() {
        // 12-second complete breath cycle: 4s inhale, 2s hold, 4s exhale, 2s rest
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 12000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                // Cycle through the 4 phases
                val phase = when {
                    fraction < 0.33f -> {
                        breathProgress = fraction / 0.33f
                        BreathPhase.INHALE
                    }
                    fraction < 0.50f -> {
                        breathProgress = 1f
                        BreathPhase.HOLD
                    }
                    fraction < 0.83f -> {
                        breathProgress = 1f - ((fraction - 0.50f) / 0.33f)
                        BreathPhase.EXHALE
                    }
                    else -> {
                        breathProgress = 0f
                        BreathPhase.STILL
                    }
                }

                if (phase != currentPhase) {
                    currentPhase = phase
                    onPhaseChangeListener?.invoke(phase)
                }

                rotationAngle = (fraction * 360f) % 360f
                updateEmbers()
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isMeditationActive) {
            animator?.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initEmbers(w.toFloat(), h.toFloat())
    }

    private fun initEmbers(w: Float, h: Float) {
        embers.clear()
        if (w <= 0 || h <= 0) return
        for (i in 0 until emberCount) {
            embers.add(
                Ember(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    speedY = 0.5f + Random.nextFloat() * 1.5f,
                    radius = 2f + Random.nextFloat() * 3.5f,
                    alpha = Random.nextInt(40, 200),
                    maxAlpha = Random.nextInt(120, 230)
                )
            )
        }
    }

    private fun updateEmbers() {
        val h = height.toFloat()
        val w = width.toFloat()
        if (h <= 0 || w <= 0) return

        for (ember in embers) {
            ember.y -= ember.speedY
            if (ember.y < 0) {
                ember.y = h + Random.nextFloat() * 20f
                ember.x = Random.nextFloat() * w
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) * 0.28f

        // 1. Draw Rising Golden Embers (Prayers rising)
        drawEmbers(canvas)

        // 2. Outer Soft Radial Glow (Pulsing divine aura)
        val outerRadius = baseRadius * (1.1f + breathProgress * 0.35f)
        glowPaint.shader = RadialGradient(
            cx, cy, outerRadius,
            intArrayOf(goldAura, Color.parseColor("#15E5B869"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerRadius, glowPaint)

        // 3. Draw Celestial Light Rays
        drawCelestialRays(canvas, cx, cy, baseRadius)

        // 4. Multiple Concentric Golden Rings
        drawConcentricRings(canvas, cx, cy, baseRadius)

        // 5. Center Sacred Cross / Divine Light
        drawCenterSacredCross(canvas, cx, cy, baseRadius * 0.35f)
    }

    private fun drawEmbers(canvas: Canvas) {
        for (ember in embers) {
            particlePaint.color = goldBright
            particlePaint.alpha = ember.alpha
            canvas.drawCircle(ember.x, ember.y, ember.radius, particlePaint)
        }
    }

    private fun drawCelestialRays(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val rayCount = 12
        val innerDist = radius * 0.55f
        val outerDist = radius * (0.85f + breathProgress * 0.25f)

        canvas.save()
        canvas.rotate(rotationAngle * 0.3f, cx, cy)

        rayPaint.strokeWidth = 2.5f
        rayPaint.color = goldBright
        rayPaint.alpha = (50 + (breathProgress * 90)).toInt().coerceIn(0, 255)

        val angleStep = (2 * Math.PI / rayCount).toFloat()
        for (i in 0 until rayCount) {
            val a = i * angleStep
            val x1 = cx + cos(a) * innerDist
            val y1 = cy + sin(a) * innerDist
            val x2 = cx + cos(a) * outerDist
            val y2 = cy + sin(a) * outerDist
            canvas.drawLine(x1, y1, x2, y2, rayPaint)
        }
        canvas.restore()
    }

    private fun drawConcentricRings(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Inner Ring
        val r1 = radius * (0.6f + breathProgress * 0.08f)
        ringPaint.strokeWidth = 1.8f
        ringPaint.color = goldPrimary
        ringPaint.alpha = 200
        canvas.drawCircle(cx, cy, r1, ringPaint)

        // Middle Ring with subtle dash
        val r2 = radius * (0.85f + breathProgress * 0.15f)
        ringPaint.strokeWidth = 2.4f
        ringPaint.color = goldBright
        ringPaint.alpha = (140 + breathProgress * 90).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r2, ringPaint)

        // Outermost Breath Wave
        val r3 = radius * (1.05f + breathProgress * 0.22f)
        ringPaint.strokeWidth = 1.2f
        ringPaint.color = emeraldAccent
        ringPaint.alpha = (60 + breathProgress * 120).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r3, ringPaint)
    }

    private fun drawCenterSacredCross(canvas: Canvas, cx: Float, cy: Float, crossSize: Float) {
        // Center soft glow
        glowPaint.shader = RadialGradient(
            cx, cy, crossSize * 1.5f,
            intArrayOf(Color.WHITE, goldBright, Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, crossSize * 1.5f, glowPaint)

        val beamThickness = crossSize * 0.22f
        val vTop = cy - crossSize
        val vBottom = cy + crossSize * 1.2f
        val hLeft = cx - crossSize * 0.75f
        val hRight = cx + crossSize * 0.75f
        val hY = cy - crossSize * 0.25f

        // Vertical beam
        canvas.drawRoundRect(
            cx - beamThickness / 2, vTop,
            cx + beamThickness / 2, vBottom,
            4f, 4f, crossPaint
        )

        // Horizontal beam
        canvas.drawRoundRect(
            hLeft, hY - beamThickness / 2,
            hRight, hY + beamThickness / 2,
            4f, 4f, crossPaint
        )
    }

    fun getCurrentPhase(): BreathPhase = currentPhase
}
