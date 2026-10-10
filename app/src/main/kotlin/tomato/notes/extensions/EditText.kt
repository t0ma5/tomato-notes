package tomato.notes.extensions

import android.text.InputFilter
import android.text.Spanned
import com.simplemobiletools.commons.views.MyEditText

fun MyEditText.enforcePlainText() {
    val stripSpans = InputFilter { source, start, end, _, _, _ ->
        if (source !is Spanned) return@InputFilter null
        val hasRealStyle = source.getSpans(start, end, Any::class.java)
            .any { span ->
                (source.getSpanFlags(span) and Spanned.SPAN_COMPOSING) == 0
            }

        if (hasRealStyle) source.subSequence(start, end).toString() else null
    }
    filters = (filters ?: emptyArray()) + stripSpans
}

fun MyEditText.safeSetSelection(position: Int) {
    val length = text?.length ?: 0
    setSelection(position.coerceIn(0, length))
}

fun MyEditText.applyReadOnlyFilters(readOnly: Boolean) {
    val next = mutableListOf<InputFilter>()
    if (readOnly) {
        next.add { source, _, _, dest, dstart, dend ->
            dest.subSequence(dstart, dend)
        }
    }
    next.add(plainTextFilter)
    filters = next.toTypedArray()
}

private val plainTextFilter = InputFilter { source, start, end, _, _, _ ->
    if (source !is Spanned) return@InputFilter null
    val hasRealStyle = source.getSpans(start, end, Any::class.java)
        .any { span ->
            (source.getSpanFlags(span) and Spanned.SPAN_COMPOSING) == 0
        }
    if (hasRealStyle) source.subSequence(start, end).toString() else null
}
