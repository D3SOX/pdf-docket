package me.d3sox.pdfwallet.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

private object Keys {
    val requireAuthentication = booleanPreferencesKey("requireAuthentication")
    val lockTimeout = stringPreferencesKey("lockTimeout")
    val barcodeSearchMode = stringPreferencesKey("barcodeSearchMode")
    val halfSizeBarcodes = booleanPreferencesKey("halfSizeBarcodes")
    val splitScreen = booleanPreferencesKey("splitScreen")
    val splitFraction = floatPreferencesKey("splitFraction")
    val showViewModeBar = booleanPreferencesKey("showViewModeBar")
    val syncPages = booleanPreferencesKey("syncPages")
    val reopenAtLastPosition = booleanPreferencesKey("reopenAtLastPosition")
    val showPageNumbers = booleanPreferencesKey("showPageNumbers")
    val generateNewBarcode = booleanPreferencesKey("generateNewBarcode")
    val fullBrightness = booleanPreferencesKey("fullBrightness")
    val showOnLockScreen = booleanPreferencesKey("showOnLockScreen")
    val preventScreenshots = booleanPreferencesKey("preventScreenshots")
    val themeMode = stringPreferencesKey("themeMode")
    val dynamicColor = booleanPreferencesKey("dynamicColor")
    val invertPdfColors = booleanPreferencesKey("invertPdfColors")
    val fillPdfWidth = booleanPreferencesKey("fillPdfWidth")
    val sortOrder = stringPreferencesKey("sortOrder")
    val manualSortExplained = booleanPreferencesKey("manualSortExplained")
    val tagFilterMode = stringPreferencesKey("tagFilterMode")
    val documentLayout = stringPreferencesKey("documentLayout")
    val deleteImportedFiles = booleanPreferencesKey("deleteImportedFiles")
    val quickSettingsDocumentId = stringPreferencesKey("quickSettingsDocumentId")
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Only writes the settings that changed, so the others keep following their defaults, e.g. a new one. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val old = prefs.toSettings()
            prefs.write(old, transform(old))
        }
    }

    // one default per setting
    @Suppress("CyclomaticComplexMethod")
    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            requireAuthentication = this[Keys.requireAuthentication] ?: defaults.requireAuthentication,
            lockTimeout = enumOrDefault(this[Keys.lockTimeout], defaults.lockTimeout),
            barcodeSearchMode = enumOrDefault(this[Keys.barcodeSearchMode], defaults.barcodeSearchMode),
            halfSizeBarcodes = this[Keys.halfSizeBarcodes] ?: defaults.halfSizeBarcodes,
            splitScreen = this[Keys.splitScreen] ?: defaults.splitScreen,
            splitFraction = this[Keys.splitFraction],
            showViewModeBar = this[Keys.showViewModeBar] ?: defaults.showViewModeBar,
            syncPages = this[Keys.syncPages] ?: defaults.syncPages,
            reopenAtLastPosition = this[Keys.reopenAtLastPosition] ?: defaults.reopenAtLastPosition,
            showPageNumbers = this[Keys.showPageNumbers] ?: defaults.showPageNumbers,
            generateNewBarcode = this[Keys.generateNewBarcode] ?: defaults.generateNewBarcode,
            fullBrightness = this[Keys.fullBrightness] ?: defaults.fullBrightness,
            showOnLockScreen = this[Keys.showOnLockScreen] ?: defaults.showOnLockScreen,
            preventScreenshots = this[Keys.preventScreenshots] ?: defaults.preventScreenshots,
            themeMode = enumOrDefault(this[Keys.themeMode], defaults.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            invertPdfColors = this[Keys.invertPdfColors] ?: defaults.invertPdfColors,
            fillPdfWidth = this[Keys.fillPdfWidth] ?: defaults.fillPdfWidth,
            sortOrder = enumOrDefault(this[Keys.sortOrder], defaults.sortOrder),
            manualSortExplained = this[Keys.manualSortExplained] ?: defaults.manualSortExplained,
            tagFilterMode = enumOrDefault(this[Keys.tagFilterMode], defaults.tagFilterMode),
            documentLayout = enumOrDefault(this[Keys.documentLayout], defaults.documentLayout),
            deleteImportedFiles = this[Keys.deleteImportedFiles] ?: defaults.deleteImportedFiles,
            quickSettingsDocumentId = this[Keys.quickSettingsDocumentId],
        )
    }

    @Suppress("LongMethod")
    private fun MutablePreferences.write(old: AppSettings, new: AppSettings) {
        fun <T> write(key: Preferences.Key<T>, value: (AppSettings) -> T) {
            if (value(new) != value(old)) this[key] = value(new)
        }
        fun writeEnum(key: Preferences.Key<String>, value: (AppSettings) -> Enum<*>) = write(key) { value(it).name }

        write(Keys.requireAuthentication) { it.requireAuthentication }
        writeEnum(Keys.lockTimeout) { it.lockTimeout }
        writeEnum(Keys.barcodeSearchMode) { it.barcodeSearchMode }
        write(Keys.halfSizeBarcodes) { it.halfSizeBarcodes }
        write(Keys.splitScreen) { it.splitScreen }
        writeOrRemove(Keys.splitFraction, old.splitFraction, new.splitFraction)
        write(Keys.showViewModeBar) { it.showViewModeBar }
        write(Keys.syncPages) { it.syncPages }
        write(Keys.reopenAtLastPosition) { it.reopenAtLastPosition }
        write(Keys.showPageNumbers) { it.showPageNumbers }
        write(Keys.generateNewBarcode) { it.generateNewBarcode }
        write(Keys.fullBrightness) { it.fullBrightness }
        write(Keys.showOnLockScreen) { it.showOnLockScreen }
        write(Keys.preventScreenshots) { it.preventScreenshots }
        writeEnum(Keys.themeMode) { it.themeMode }
        write(Keys.dynamicColor) { it.dynamicColor }
        write(Keys.invertPdfColors) { it.invertPdfColors }
        write(Keys.fillPdfWidth) { it.fillPdfWidth }
        writeEnum(Keys.sortOrder) { it.sortOrder }
        write(Keys.manualSortExplained) { it.manualSortExplained }
        writeEnum(Keys.tagFilterMode) { it.tagFilterMode }
        writeEnum(Keys.documentLayout) { it.documentLayout }
        write(Keys.deleteImportedFiles) { it.deleteImportedFiles }
        writeOrRemove(Keys.quickSettingsDocumentId, old.quickSettingsDocumentId, new.quickSettingsDocumentId)
    }

    private fun <T> MutablePreferences.writeOrRemove(key: Preferences.Key<T>, old: T?, new: T?) {
        if (new != old) new?.let { this[key] = it } ?: remove(key)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default
}
