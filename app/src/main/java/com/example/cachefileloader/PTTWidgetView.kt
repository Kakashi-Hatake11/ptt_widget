package com.example.cachefileloader

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/**
 * PTTWidgetView
 *
 * A custom View that renders a real-time audio waveform visualizer
 * for the Push-to-Talk widget. When idle, it shows a flat pulse line.
 * When transmitting, it animates reactive bars driven by mic amplitude.
 */
class PTTWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── State ──────────────────────────────────────────────────────────────────
    private var isTransmitting = false
    private var currentAmplitude = 0f   // 0.0 – 1.0 normalised
    private var animFrame = 0
    private val barCount = 28
    private val barHeights = FloatArray(barCount) { 0.08f }
    private val targetHeights = FloatArray(barCount) { 0.08f }

    // ── Paints ─────────────────────────────────────────────────────────────────
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A4A5C")
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    }

    // ── Gradient (set in onSizeChanged) ────────────────────────────────────────
    private var activeGradient: LinearGradient? = null
    private var glowGradient: LinearGradient? = null

    // ── Runnable for animation loop ────────────────────────────────────────────
    private val animRunnable = object : Runnable {
        override fun run() {
            tick()
            invalidate()
            if (isTransmitting || barHeights.any { it > 0.09f }) {
                postDelayed(this, 40) // ~25 fps
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun startVisualizer() {
        isTransmitting = true
        removeCallbacks(animRunnable)
        post(animRunnable)
    }

    fun stopVisualizer() {
        isTransmitting = false
        currentAmplitude = 0f
        // Let the loop decay naturally, then stop
    }

    /**
     * Feed normalised amplitude (0–1) from AudioRecorderManager.
     */
    fun updateAmplitude(amplitude: Float) {
        currentAmplitude = amplitude.coerceIn(0f, 1f)
        generateTargetHeights()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun generateTargetHeights() {
        val base = currentAmplitude
        for (i in 0 until barCount) {
            val center = barCount / 2f
            val distFromCenter = abs(i - center) / center          // 0 at center, 1 at edge
            val envelope = 1f - distFromCenter * 0.6f              // centre bars taller
            val noise = Random.nextFloat() * 0.25f
            targetHeights[i] = (base * envelope + noise * base + 0.05f).coerceIn(0.04f, 1f)
        }
    }

    private fun tick() {
        animFrame++
        val lerpSpeed = if (isTransmitting) 0.35f else 0.18f
        for (i in 0 until barCount) {
            val target = if (isTransmitting) targetHeights[i] else 0.06f
            barHeights[i] += (target - barHeights[i]) * lerpSpeed
        }
    }

    // ── Drawing ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        activeGradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#00B4D8"),
                Color.parseColor("#0077B6")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        glowGradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#8000E5FF"),
                Color.parseColor("#4000B4D8"),
                Color.parseColor("#000077B6")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        activePaint.shader = activeGradient
        glowPaint.shader = glowGradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        val totalBarWidth = w * 0.85f
        val barW = totalBarWidth / (barCount * 1.6f)
        val gap = barW * 0.6f
        val step = barW + gap
        val startX = cx - (barCount / 2f) * step

        for (i in 0 until barCount) {
            val bh = barHeights[i] * h * 0.88f
            val x = startX + i * step
            val top = cy - bh / 2f
            val bottom = cy + bh / 2f
            val rect = RectF(x, top, x + barW, bottom)
            val radius = barW / 2f

            if (isTransmitting || barHeights[i] > 0.09f) {
                // Glow layer
                canvas.drawRoundRect(rect, radius, radius, glowPaint)
                // Solid bar
                canvas.drawRoundRect(rect, radius, radius, activePaint)
            } else {
                canvas.drawRoundRect(rect, radius, radius, idlePaint)
            }
        }
    }
}
