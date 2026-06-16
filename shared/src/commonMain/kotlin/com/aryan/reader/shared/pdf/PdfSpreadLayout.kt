package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings

fun interface PdfPageSizeProvider {
    fun isLandscape(pageIndex: Int): Boolean
}

object PdfSpreadLayout {
    fun isTwoPageSpreadEnabled(settings: ReaderSettings): Boolean {
        return settings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE || 
               settings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE_ADAPTIVE
    }

    fun spreadStartPageIndices(
        pageCount: Int,
        settings: ReaderSettings,
        pageSizeProvider: PdfPageSizeProvider? = null
    ): List<Int> {
        if (pageCount <= 0) return emptyList()
        if (!isTwoPageSpreadEnabled(settings)) return (0 until pageCount).toList()

        val starts = mutableListOf<Int>()
        var current = 0
        val isAdaptive = settings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE_ADAPTIVE

        while (current < pageCount) {
            starts.add(current)
            if (settings.pdfFirstPageStandaloneInSpread && current == 0) {
                current += 1
                continue
            }
            if (isAdaptive && pageSizeProvider?.isLandscape(current) == true) {
                current += 1
                continue
            }
            val next = current + 1
            if (next < pageCount && isAdaptive && pageSizeProvider?.isLandscape(next) == true) {
                current += 1
                continue
            }
            current += 2
        }
        return starts
    }

    fun normalizePageIndex(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): Int {
        if (pageCount <= 0) return 0
        val clamped = pageIndex.coerceIn(0, pageCount - 1)
        if (!isTwoPageSpreadEnabled(settings)) return clamped

        if (spreadStarts != null && spreadStarts.isNotEmpty()) {
            var idx = spreadStarts.binarySearch(clamped)
            if (idx < 0) {
                idx = -(idx + 1) - 1
            }
            return spreadStarts.getOrNull(idx) ?: 0
        }

        if (!settings.pdfFirstPageStandaloneInSpread) {
            return (clamped - (clamped % 2)).coerceIn(0, pageCount - 1)
        }
        if (clamped == 0) return 0
        val adjusted = clamped - 1
        return (1 + adjusted - (adjusted % 2)).coerceIn(0, pageCount - 1)
    }

    fun visiblePageIndices(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): List<Int> {
        if (pageCount <= 0) return emptyList()
        val start = normalizePageIndex(pageIndex, pageCount, settings, spreadStarts)
        if (!isTwoPageSpreadEnabled(settings)) return listOf(start)

        val indices = if (spreadStarts != null && spreadStarts.isNotEmpty()) {
            val idx = spreadStarts.binarySearch(start)
            if (idx >= 0) {
                val nextStart = spreadStarts.getOrNull(idx + 1) ?: pageCount
                (start until nextStart).toList()
            } else {
                listOf(start)
            }
        } else {
            if (settings.pdfFirstPageStandaloneInSpread && start == 0) listOf(0)
            else listOf(start, start + 1).filter { it in 0 until pageCount }
        }

        return if (settings.pdfPageSpreadFlipped && indices.size == 2) {
            indices.reversed()
        } else {
            indices
        }
    }

    fun visiblePageIndicesForDisplay(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): List<Int> {
        val indices = visiblePageIndices(pageIndex, pageCount, settings, spreadStarts)
        return if (settings.rightToLeftPagination) indices.asReversed() else indices
    }


    fun canGoPrevious(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): Boolean {
        if (pageCount <= 1) return false
        return previousPageIndex(pageIndex, pageCount, settings, spreadStarts) < normalizePageIndex(pageIndex, pageCount, settings, spreadStarts)
    }

    fun canGoNext(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): Boolean {
        if (pageCount <= 1) return false
        return nextPageIndex(pageIndex, pageCount, settings, spreadStarts) > normalizePageIndex(pageIndex, pageCount, settings, spreadStarts)
    }

    fun previousPageIndex(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): Int {
        if (pageCount <= 0) return 0
        val current = normalizePageIndex(pageIndex, pageCount, settings, spreadStarts)
        if (!isTwoPageSpreadEnabled(settings)) {
            return (current - 1).coerceIn(0, pageCount - 1)
        }

        if (spreadStarts != null && spreadStarts.isNotEmpty()) {
            val idx = spreadStarts.binarySearch(current)
            if (idx > 0) {
                return spreadStarts[idx - 1]
            }
            return spreadStarts[0]
        }

        val target = if (settings.pdfFirstPageStandaloneInSpread && current <= 1) {
            0
        } else {
            current - 2
        }
        return normalizePageIndex(target, pageCount, settings)
    }

    fun nextPageIndex(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): Int {
        if (pageCount <= 0) return 0
        val current = normalizePageIndex(pageIndex, pageCount, settings, spreadStarts)
        if (!isTwoPageSpreadEnabled(settings)) {
            return (current + 1).coerceIn(0, pageCount - 1)
        }

        if (spreadStarts != null && spreadStarts.isNotEmpty()) {
            val idx = spreadStarts.binarySearch(current)
            if (idx >= 0 && idx < spreadStarts.lastIndex) {
                return spreadStarts[idx + 1]
            }
            return spreadStarts.last()
        }

        val target = if (settings.pdfFirstPageStandaloneInSpread && current == 0) {
            1
        } else {
            current + 2
        }
        return normalizePageIndex(target, pageCount, settings)
    }

    fun pageRangeLabel(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): String {
        val pages = visiblePageIndices(pageIndex, pageCount.coerceAtLeast(1), settings, spreadStarts).ifEmpty { listOf(0) }.sorted()
        val first = pages.first() + 1
        val last = pages.last() + 1
        return if (first == last) "$first" else "$first-$last"
    }

    fun progressPercent(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings,
        spreadStarts: List<Int>? = null
    ): Float {
        if (pageCount <= 0) return 0f
        val visibleEnd = visiblePageIndices(pageIndex, pageCount, settings, spreadStarts).maxOrNull()
            ?: normalizePageIndex(pageIndex, pageCount, settings, spreadStarts)
        return ((visibleEnd + 1).toFloat() / pageCount.coerceAtLeast(1)) * 100f
    }
}
