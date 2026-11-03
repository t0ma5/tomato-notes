package tomato.notes.helpers

import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MyMovementMethod : ArrowKeyMovementMethod() {
    companion object {
        private var sInstance: MyMovementMethod? = null

        fun getInstance(): MyMovementMethod {
            if (sInstance == null) {
                sInstance = MyMovementMethod()
            }
            return sInstance!!
        }
    }

    private var pressedLink: ClickableSpan? = null
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedLink = findLinkAt(widget, buffer, event)
                downX = event.x
                downY = event.y
                pressedLink?.let {
                    Selection.setSelection(buffer, buffer.getSpanStart(it), buffer.getSpanEnd(it))
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                val link = pressedLink
                pressedLink = null
                if (link != null) {
                    // Only a tap opens the link: a drag, scroll or long press that started on it does not
                    val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
                    val isTap = abs(event.x - downX) <= slop && abs(event.y - downY) <= slop &&
                        event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()
                    if (isTap && findLinkAt(widget, buffer, event) === link) {
                        link.onClick(widget)
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> pressedLink = null
        }

        return super.onTouchEvent(widget, buffer, event)
    }

    // The link drawn under the finger, or null. Taps beside or below the text don't snap to the nearest link.
    private fun findLinkAt(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan? {
        val layout: Layout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        if (y < 0 || y > layout.height) {
            return null
        }

        val line = layout.getLineForVertical(y.toInt())
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null
        }

        val offset = layout.getOffsetForHorizontal(line, x)
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        return buffer.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull { link ->
            val start = max(buffer.getSpanStart(link), lineStart)
            val end = min(buffer.getSpanEnd(link), lineEnd)
            if (start >= end) {
                return@firstOrNull false
            }
            val startX = layout.getPrimaryHorizontal(start)
            // On a wrapped line, the line end offset belongs to the next line, so use the line's own edge
            val endX = if (end >= lineEnd) layout.getLineRight(line) else layout.getPrimaryHorizontal(end)
            val left = min(startX, endX)
            val right = max(startX, endX)
            x in left..right
        }
    }
}
