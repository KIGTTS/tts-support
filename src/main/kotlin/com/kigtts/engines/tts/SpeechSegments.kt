// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.tts

/** Small first sentence for early audio, larger following sentences; preserves all source text. */
fun speechSegments(text: String, firstLimit: Int = 32, nextLimit: Int = 96): Sequence<String> = sequence {
    require(firstLimit >= 2 && nextLimit >= 2)
    var start = 0
    while (start < text.length) {
        val limit = if (start == 0) firstLimit else nextLimit
        var end = minOf(text.length, start + limit)
        if (end < text.length && text[end - 1].isHighSurrogate()) end--
        val window = text.substring(start, end)
        val sentence = window.indices.firstOrNull { index ->
            window[index] in "。！？!?\n；;" || window[index] == '.' &&
                (index == window.lastIndex || !window[index + 1].isDigit()) && (index == 0 || !window[index - 1].isDigit())
        }
        if (sentence != null) end = start + sentence + 1
        else if (end < text.length) {
            val breakAt = window.indexOfLast { it.isWhitespace() || it in "，,、：:" }
            if (breakAt >= window.length / 3) end = start + breakAt + 1
        }
        yield(text.substring(start, end))
        start = end
    }
}
