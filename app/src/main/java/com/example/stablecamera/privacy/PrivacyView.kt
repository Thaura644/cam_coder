package com.example.stablecamera.privacy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

import android.graphics.*
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class PrivacyOverlayView(context: Context) : View(context) {
    private val privacyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rainbowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rainbowShader: LinearGradient? = null
    private val shaderMatrix = Matrix()
    private var shaderOffset = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            shaderOffset = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // "Narrow Viewing Angle" effect: a more aggressive radial gradient
        privacyPaint.shader = RadialGradient(
            w / 2f, h / 2f, Math.min(w, h) * 0.5f,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.4f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // "Rainbow Lights" aesthetic indicator
        rainbowShader = LinearGradient(
            0f, 0f, w.toFloat() / 2f, 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null,
            Shader.TileMode.REPEAT
        )
        rainbowPaint.shader = rainbowShader
    }

    override fun onDraw(canvas: Canvas) {
        // Update shader matrix for animation
        shaderMatrix.setTranslate(shaderOffset * width.toFloat() / 2f, 0f)
        rainbowShader?.setLocalMatrix(shaderMatrix)

        // Draw the privacy filter (Narrow Viewing Angle)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), privacyPaint)

        // Draw the rainbow light strip
        canvas.drawRect(0f, 0f, width.toFloat(), 20f, rainbowPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
