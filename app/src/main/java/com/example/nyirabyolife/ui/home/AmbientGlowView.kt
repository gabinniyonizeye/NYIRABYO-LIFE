package com.example.nyirabyolife.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.nyirabyolife.data.local.PreferencesManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * AmbientGlowView renders a continuous, meditative, slow-breathing ambient aura
 * behind the launcher home screen. It dynamically adapts its luminescence palette
 * to the user's selected Holy Habitation Theme.
 */
class AmbientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var animProgress = 0f
    private var animator: ValueAnimator? = null
    private val prefs = PreferencesManager.getInstance(context)

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        startAmbientAnimation()
    }

    private fun startAmbientAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 22000L // 22-second deep peaceful cycle
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null || !animator!!.isRunning) {
            startAmbientAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val phase = animProgress * 2 * Math.PI.toFloat()
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(context)
        val rgb1 = themeColors.rgb1
        val rgb2 = themeColors.rgb2
        val rgb3 = themeColors.rgb3

        val transparentColor = Color.TRANSPARENT

        // Glow 1: Primary aura (Top-Left quadrant, gentle orbit)
        val cx1 = w * 0.25f + sin(phase) * (w * 0.12f)
        val cy1 = h * 0.22f + cos(phase) * (h * 0.08f)
        val radius1 = w * 0.85f + sin(phase * 0.5f) * (w * 0.15f)

        val alpha1 = (26 + 10 * sin(phase)).toInt().coerceIn(16, 40)
        val color1 = Color.argb(alpha1, rgb1.first, rgb1.second, rgb1.third)

        val gradient1 = RadialGradient(
            cx1, cy1, radius1,
            intArrayOf(color1, transparentColor),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        auraPaint.shader = gradient1
        canvas.drawCircle(cx1, cy1, radius1, auraPaint)

        // Glow 2: Secondary aura (Center-Right, counter-phase)
        val cx2 = w * 0.78f - cos(phase * 0.8f) * (w * 0.14f)
        val cy2 = h * 0.55f + sin(phase * 0.8f) * (h * 0.10f)
        val radius2 = w * 0.90f + cos(phase * 0.6f) * (w * 0.12f)

        val alpha2 = (22 + 8 * cos(phase)).toInt().coerceIn(14, 34)
        val color2 = Color.argb(alpha2, rgb2.first, rgb2.second, rgb2.third)

        val gradient2 = RadialGradient(
            cx2, cy2, radius2,
            intArrayOf(color2, transparentColor),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        auraPaint.shader = gradient2
        canvas.drawCircle(cx2, cy2, radius2, auraPaint)

        // Glow 3: Base foundation aura (Bottom Center, deep breath)
        val cx3 = w * 0.50f + sin(phase * 1.2f) * (w * 0.10f)
        val cy3 = h * 0.88f - cos(phase * 0.7f) * (h * 0.06f)
        val radius3 = w * 0.80f + sin(phase * 0.9f) * (w * 0.10f)

        val alpha3 = (20 + 7 * sin(phase * 1.1f)).toInt().coerceIn(12, 30)
        val color3 = Color.argb(alpha3, rgb3.first, rgb3.second, rgb3.third)

        val gradient3 = RadialGradient(
            cx3, cy3, radius3,
            intArrayOf(color3, transparentColor),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        auraPaint.shader = gradient3
        canvas.drawCircle(cx3, cy3, radius3, auraPaint)
    }
}
