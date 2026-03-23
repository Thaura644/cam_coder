package com.example.stablecamera.privacy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

class PrivacyOverlayView(context: Context) : View(context) {
    private val privacyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rainbowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rainbowShader: LinearGradient? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // "Narrow Viewing Angle" effect: radial gradient from transparent center to dark edges
        privacyPaint.shader = RadialGradient(
            w / 2f, h / 2f, Math.min(w, h) * 0.4f,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // "Rainbow Lights" aesthetic indicator at the top
        rainbowShader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA),
            null,
            Shader.TileMode.REPEAT
        )
        rainbowPaint.shader = rainbowShader
    }

    override fun onDraw(canvas: Canvas) {
        // Draw the privacy filter
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), privacyPaint)

        // Draw the rainbow light strip
        canvas.drawRect(0f, 0f, width.toFloat(), 15f, rainbowPaint)
    }
}
