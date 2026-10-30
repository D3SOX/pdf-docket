package me.d3sox.pdfwallet.settings

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class BarcodeSearchMode { DISABLED, REGULAR, EXTENDED }

@Suppress("MagicNumber")
enum class LockTimeout(val duration: Duration) {
    /** locks when the app is left */
    IMMEDIATELY(Duration.ZERO),
    ONE_MINUTE(1.minutes),
    FIVE_MINUTES(5.minutes),
    FIFTEEN_MINUTES(15.minutes),
    THIRTY_MINUTES(30.minutes),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TagFilterMode { ALL, ANY }

enum class DocumentLayout { GRID, LIST }

enum class SortOrder {
    IMPORT_DATE_DESC,
    IMPORT_DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
}

data class AppSettings(
    val requireAuthentication: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.FIVE_MINUTES,
    val barcodeSearchMode: BarcodeSearchMode = BarcodeSearchMode.EXTENDED,
    val halfSizeBarcodes: Boolean = false,
    val splitScreen: Boolean = false,
    val syncPages: Boolean = true,
    val reopenAtLastPosition: Boolean = true,
    val showPageNumbers: Boolean = true,
    val generateNewBarcode: Boolean = false,
    val fullBrightness: Boolean = false,
    val showOnLockScreen: Boolean = false,
    val preventScreenshots: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val invertPdfColors: Boolean = false,
    val sortOrder: SortOrder = SortOrder.IMPORT_DATE_DESC,
    val tagFilterMode: TagFilterMode = TagFilterMode.ALL,
    val documentLayout: DocumentLayout = DocumentLayout.GRID,
) {
    /**
     * Screenshots stay blocked while authentication is enabled, so app content can't leak via "recent apps".
     */
    val blockScreenshots: Boolean get() = preventScreenshots || requireAuthentication
}
