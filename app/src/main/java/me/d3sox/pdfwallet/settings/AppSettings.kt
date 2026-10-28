package me.d3sox.pdfwallet.settings

enum class BarcodeSearchMode { DISABLED, REGULAR, EXTENDED }

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
