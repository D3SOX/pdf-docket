package me.d3sox.pdfwallet.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val barcodeSearchMode = stringPreferencesKey("barcodeSearchMode")
    val halfSizeBarcodes = booleanPreferencesKey("halfSizeBarcodes")
    val splitScreen = booleanPreferencesKey("splitScreen")
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
    val sortOrder = stringPreferencesKey("sortOrder")
    val tagFilterMode = stringPreferencesKey("tagFilterMode")
    val documentLayout = stringPreferencesKey("documentLayout")
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            prefs.write(transform(prefs.toSettings()))
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            requireAuthentication = this[Keys.requireAuthentication] ?: defaults.requireAuthentication,
            barcodeSearchMode = enumOrDefault(this[Keys.barcodeSearchMode], defaults.barcodeSearchMode),
            halfSizeBarcodes = this[Keys.halfSizeBarcodes] ?: defaults.halfSizeBarcodes,
            splitScreen = this[Keys.splitScreen] ?: defaults.splitScreen,
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
            sortOrder = enumOrDefault(this[Keys.sortOrder], defaults.sortOrder),
            tagFilterMode = enumOrDefault(this[Keys.tagFilterMode], defaults.tagFilterMode),
            documentLayout = enumOrDefault(this[Keys.documentLayout], defaults.documentLayout),
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.requireAuthentication] = settings.requireAuthentication
        this[Keys.barcodeSearchMode] = settings.barcodeSearchMode.name
        this[Keys.halfSizeBarcodes] = settings.halfSizeBarcodes
        this[Keys.splitScreen] = settings.splitScreen
        this[Keys.syncPages] = settings.syncPages
        this[Keys.reopenAtLastPosition] = settings.reopenAtLastPosition
        this[Keys.showPageNumbers] = settings.showPageNumbers
        this[Keys.generateNewBarcode] = settings.generateNewBarcode
        this[Keys.fullBrightness] = settings.fullBrightness
        this[Keys.showOnLockScreen] = settings.showOnLockScreen
        this[Keys.preventScreenshots] = settings.preventScreenshots
        this[Keys.themeMode] = settings.themeMode.name
        this[Keys.dynamicColor] = settings.dynamicColor
        this[Keys.invertPdfColors] = settings.invertPdfColors
        this[Keys.sortOrder] = settings.sortOrder.name
        this[Keys.tagFilterMode] = settings.tagFilterMode.name
        this[Keys.documentLayout] = settings.documentLayout.name
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default
}
