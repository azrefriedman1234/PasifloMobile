package com.ahla.pasiflonetmobile.util
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
class DrawOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var mode = 0 
    var blurRect: RectF? = null
    var watermarkPoint: PointF? = null
    private val paintBlur = Paint().apply { color = Color.parseColor("#80FF0000"); style = Paint.Style.STROKE; strokeWidth = 5f }
    private val paintWm = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL; alpha = 150 }
    private var startP: PointF? = null
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode == 0) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> startP = PointF(event.x, event.y)
            MotionEvent.ACTION_MOVE -> if (mode == 1 && startP != null) { blurRect = RectF(startP!!.x, startP!!.y, event.x, event.y); invalidate() }
            MotionEvent.ACTION_UP -> if (mode == 2) { watermarkPoint = PointF(event.x, event.y); invalidate() }
        }
        return true
    }
    override fun onDraw(canvas: Canvas) { blurRect?.let { canvas.drawRect(it, paintBlur) }; watermarkPoint?.let { canvas.drawCircle(it.x, it.y, 30f, paintWm) } }
    fun getRelativeBlur(): RectF? { val r = blurRect ?: return null; return if (width > 0) RectF(r.left / width, r.top / height, r.right / width, r.bottom / height) else null }
    fun getRelativeWatermark(): PointF? { val p = watermarkPoint ?: return null; return if (width > 0) PointF(p.x / width, p.y / height) else null }
}
