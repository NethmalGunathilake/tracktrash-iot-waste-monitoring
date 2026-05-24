package com.harindu.TrakTrash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class BinFillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var fillLevel: Int = 0 // Percentage from 0 to 100

    // Paints
    private val binBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val binOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lidHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // RectF and Paths for drawing
    private val binBodyRect = RectF()
    private val fillRect = RectF()
    private val lidPath = Path()
    private val basePath = Path()
    private val lidBounds = RectF()

    // Dimensions
    private var lidHeightRatio = 0.15f // 15% of total height for lid
    private var baseHeightRatio = 0.10f // 10% of total height for base
    private var binTopWidthRatio = 0.8f // Bin top width as % of view width
    private var binBottomWidthRatio = 0.7f // Bin bottom width as % of view width
    private var lidHandleWidthRatio = 0.2f // Lid handle width as % of view width
    private var lidHandleHeightRatio = 0.03f // Lid handle height as % of total height

    init {
        // Bin Body (background)
        binBodyPaint.color = Color.parseColor("#F5F5F5")
        binBodyPaint.style = Paint.Style.FILL

        // Bin Outline
        binOutlinePaint.color = Color.parseColor("#616161")
        binOutlinePaint.style = Paint.Style.STROKE
        binOutlinePaint.strokeWidth = 4f

        // Fill Level Text
        textPaint.color = Color.WHITE
        textPaint.textSize = 40f
        textPaint.textAlign = Paint.Align.CENTER

        // Lid
        lidPaint.color = Color.parseColor("#757575")
        lidPaint.style = Paint.Style.FILL

        // Lid Handle
        lidHandlePaint.color = Color.parseColor("#424242")
        lidHandlePaint.style = Paint.Style.FILL

        // Base
        basePaint.color = Color.parseColor("#424242")
        basePaint.style = Paint.Style.FILL
    }

    /**
     * Sets the fill level of the bin.
     * @param level The fill level as a percentage (0-100).
     */
    fun setFillLevel(level: Int) {
        this.fillLevel = level.coerceIn(0, 100) // Ensure level is between 0 and 100
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val padding = 10f // Overall padding from view edges
        val effectiveWidth = w - (padding * 2)
        val effectiveHeight = h - (padding * 2)

        val lidHeight = effectiveHeight * lidHeightRatio
        val baseHeight = effectiveHeight * baseHeightRatio
        val binBodyHeight = effectiveHeight - lidHeight - baseHeight

        val binTopWidth = effectiveWidth * binTopWidthRatio
        val binBottomWidth = effectiveWidth * binBottomWidthRatio

        val centerX = w / 2f
        val topY = padding
        val bottomY = h - padding

        // Calculate points for the trapezoidal bin body
        val bodyLeftTop = centerX - (binTopWidth / 2)
        val bodyRightTop = centerX + (binTopWidth / 2)
        val bodyLeftBottom = centerX - (binBottomWidth / 2)
        val bodyRightBottom = centerX + (binBottomWidth / 2)

        // Bin Body RectF (for fill and background)
        binBodyRect.set(bodyLeftBottom, topY + lidHeight, bodyRightBottom, bottomY - baseHeight)

        // Lid Path
        lidPath.reset()
        lidPath.moveTo(bodyLeftTop, topY + lidHeight) // Bottom-left of lid (top-left of bin body)
        lidPath.lineTo(bodyRightTop, topY + lidHeight) // Bottom-right of lid (top-right of bin body)
        lidPath.lineTo(bodyRightTop + (effectiveWidth * 0.05f), topY + lidHeight - (lidHeight * 0.5f)) // Top-right of lid
        lidPath.lineTo(bodyLeftTop - (effectiveWidth * 0.05f), topY + lidHeight - (lidHeight * 0.5f)) // Top-left of lid
        lidPath.close()

        // Compute lid bounds here after lidPath is defined
        lidPath.computeBounds(lidBounds, true) // ADDED: Compute bounds for the lid path

        // Base Path (trapezoidal base)
        basePath.reset()
        basePath.moveTo(binBodyRect.left, binBodyRect.bottom) // Bottom-left of bin body
        basePath.lineTo(binBodyRect.right, binBodyRect.bottom) // Bottom-right of bin body
        basePath.lineTo(bodyRightBottom + (effectiveWidth * 0.03f), bottomY) // Bottom-right of base
        basePath.lineTo(bodyLeftBottom - (effectiveWidth * 0.03f), bottomY) // Bottom-left of base
        basePath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw bin body background (empty part)
        canvas.drawRect(binBodyRect, binBodyPaint)

        // Calculate fill height
        val fillHeight = binBodyRect.height() * (fillLevel / 100f)
        val fillTop = binBodyRect.bottom - fillHeight

        // Set fill color and gradient
        val startColor: Int
        val endColor: Int

        when {
            fillLevel >= 80 -> { // Red for high fill
                startColor = Color.parseColor("#F44336")
                endColor = Color.parseColor("#D32F2F")
            }
            fillLevel >= 50 -> { // Orange for medium fill
                startColor = Color.parseColor("#FFEB3B")
                endColor = Color.parseColor("#FFC107")
            }
            else -> { // Green for low fill
                startColor = Color.parseColor("#8BC34A")
                endColor = Color.parseColor("#4CAF50")
            }
        }

        fillPaint.shader = LinearGradient(
            binBodyRect.centerX(), fillTop,
            binBodyRect.centerX(), binBodyRect.bottom,
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        fillPaint.style = Paint.Style.FILL

        // Draw fill level
        fillRect.set(binBodyRect.left, fillTop, binBodyRect.right, binBodyRect.bottom)
        canvas.drawRect(fillRect, fillPaint)

        // Draw bin body outline
        canvas.drawRect(binBodyRect, binOutlinePaint)

        // Draw Lid
        canvas.drawPath(lidPath, lidPaint)

        // Draw lid handle
        val handleWidth = width * lidHandleWidthRatio
        val handleHeight = height * lidHandleHeightRatio
        val handleRect = RectF(
            lidBounds.centerX() - handleWidth / 2f, // Use lidBounds.centerX()
            lidBounds.top - handleHeight, // Use lidBounds.top
            lidBounds.centerX() + handleWidth / 2f,
            lidBounds.top
        )
        canvas.drawRoundRect(handleRect, handleHeight / 2, handleHeight / 2, lidHandlePaint)


        // Draw Base
        canvas.drawPath(basePath, basePaint)

        // Draw Fill Level Text
        if (fillLevel > 5) {
            val text = "$fillLevel%"
            val xPos = binBodyRect.centerX()
            val textHeight = textPaint.descent() - textPaint.ascent()
            val textOffset = textHeight / 2 - textPaint.descent()
            val yPos = fillTop + (binBodyRect.bottom - fillTop) / 2 + textOffset
            canvas.drawText(text, xPos, yPos, textPaint)
        }
    }
}