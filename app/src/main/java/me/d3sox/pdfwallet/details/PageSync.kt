package me.d3sox.pdfwallet.details

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Moves one pager along with the other while it's swiped, so the PDF shows the page of the current barcode,
 * and the barcodes one on the current PDF page. [barcodePages] are the page indexes of the barcodes.
 *
 * Only the pager the user swiped last leads, so the pagers never chase each other. With [alignAtStart], the PDF
 * starts at the page of the first barcode, otherwise both pagers stay where they are until one is swiped.
 */
@Composable
internal fun SyncPagers(
    barcodePager: PagerState,
    pdfPager: PagerState,
    alignAtStart: Boolean,
    barcodePages: () -> List<Int>,
) {
    LaunchedEffect(barcodePager, pdfPager) {
        // null until the user swipes one of them, unless aligned at the start
        var barcodesLead by mutableStateOf(if (alignAtStart) true else null)
        // the barcode shown when the PDF was touched, which stays while the PDF is on its page
        var barcodeAtDragStart by mutableIntStateOf(0)
        launch {
            barcodePager.interactionSource.interactions.collect {
                if (it is DragInteraction.Start) barcodesLead = true
            }
        }
        launch {
            pdfPager.interactionSource.interactions.collect {
                if (it is DragInteraction.Start) {
                    barcodeAtDragStart = barcodePager.currentPage
                    barcodesLead = false
                }
            }
        }
        snapshotFlow {
            val pages = barcodePages()
            when {
                pages.isEmpty() -> null
                barcodesLead == true -> pdfPager to pdfPosition(barcodePager.position, pages)
                barcodesLead == false ->
                    barcodePager to barcodePosition(pdfPager.position, pages, keep = barcodeAtDragStart)
                else -> null
            }
        }
            .filterNotNull()
            .collect { (follower, position) ->
                val page = position.roundToInt()
                if (follower.currentPage != page || follower.currentPageOffsetFraction != position - page) {
                    follower.requestScrollToPage(page, pageOffsetFraction = position - page)
                }
            }
    }
}

private val PagerState.position: Float get() = currentPage + currentPageOffsetFraction

/**
 * The PDF position for a position in the barcode pager, moving between the pages of neighboring barcodes.
 */
internal fun pdfPosition(barcodePosition: Float, barcodePages: List<Int>): Float {
    val position = barcodePosition.coerceIn(0f, barcodePages.lastIndex.toFloat())
    val index = floor(position).toInt()
    val next = barcodePages.getOrNull(index + 1) ?: return barcodePages[index].toFloat()
    return barcodePages[index] + (position - index) * (next - barcodePages[index])
}

/**
 * The barcode position for a position in the PDF pager. The barcodes stay on the last barcode before the page,
 * and only move on while the PDF turns to the page of the next barcode, so they never rest between two barcodes.
 * On pages with several barcodes, the barcode at index [keep] stays if it's on the page, otherwise the first one
 * is shown.
 */
internal fun barcodePosition(pdfPosition: Float, barcodePages: List<Int>, keep: Int = -1): Float {
    val previousPage = barcodePages.lastOrNull { it <= floor(pdfPosition) } ?: return 0f
    val nextIndex = barcodePages.indexOfFirst { it > previousPage }
    val previous = barcodePages.barcodeOnPage(previousPage, keep)
    val progress = if (nextIndex == -1) 0f else (pdfPosition - (barcodePages[nextIndex] - 1)).coerceIn(0f, 1f)
    val next = if (nextIndex == -1) previous else barcodePages.barcodeOnPage(barcodePages[nextIndex], keep)
    return previous + progress * (next - previous)
}

private fun List<Int>.barcodeOnPage(page: Int, keep: Int): Int = if (getOrNull(keep) == page) keep else indexOf(page)
