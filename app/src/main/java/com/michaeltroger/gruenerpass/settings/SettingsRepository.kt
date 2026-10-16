package com.michaeltroger.gruenerpass.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
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

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
    produceMigrations = { context ->
        // values were stored in the default SharedPreferences by earlier app versions
        listOf(SharedPreferencesMigration(context, "${context.packageName}_preferences"))
    },
)

// Keys and values match those of the former SharedPreferences, so they migrate as is.
private object Keys {
    val requireAuthentication = booleanPreferencesKey("shouldAuthenticate")
    val barcodeSearchMode = stringPreferencesKey("extractBarcodes")
    val halfSizeBarcodes = booleanPreferencesKey("halfSizeBarcodes")
    val generateNewBarcode = booleanPreferencesKey("newBarcodeGeneration")
    val fullBrightness = booleanPreferencesKey("fullBrightness")
    val showOnLockScreen = booleanPreferencesKey("showOnLockedScreen")
    val preventScreenshots = booleanPreferencesKey("preventScreenshots")
    val themeMode = stringPreferencesKey("forceTheme")
    val dynamicColor = booleanPreferencesKey("dynamicColor")
    val invertPdfColors = booleanPreferencesKey("invertPdfColors")
    val sortOrder = stringPreferencesKey("sortOrder")
    val tagFilterMode = stringPreferencesKey("tag_filter_type")
}

private const val TAG_FILTER_ANY = "OR"
private const val TAG_FILTER_ALL = "AND"

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
            generateNewBarcode = this[Keys.generateNewBarcode] ?: defaults.generateNewBarcode,
            fullBrightness = this[Keys.fullBrightness] ?: defaults.fullBrightness,
            showOnLockScreen = this[Keys.showOnLockScreen] ?: defaults.showOnLockScreen,
            preventScreenshots = this[Keys.preventScreenshots] ?: defaults.preventScreenshots,
            themeMode = enumOrDefault(this[Keys.themeMode], defaults.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            invertPdfColors = this[Keys.invertPdfColors] ?: defaults.invertPdfColors,
            sortOrder = enumOrDefault(this[Keys.sortOrder], defaults.sortOrder),
            tagFilterMode = when (this[Keys.tagFilterMode]) {
                TAG_FILTER_ANY -> TagFilterMode.ANY
                else -> TagFilterMode.ALL
            },
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.requireAuthentication] = settings.requireAuthentication
        this[Keys.barcodeSearchMode] = settings.barcodeSearchMode.name.lowercase()
        this[Keys.halfSizeBarcodes] = settings.halfSizeBarcodes
        this[Keys.generateNewBarcode] = settings.generateNewBarcode
        this[Keys.fullBrightness] = settings.fullBrightness
        this[Keys.showOnLockScreen] = settings.showOnLockScreen
        this[Keys.preventScreenshots] = settings.preventScreenshots
        this[Keys.themeMode] = settings.themeMode.name.lowercase()
        this[Keys.dynamicColor] = settings.dynamicColor
        this[Keys.invertPdfColors] = settings.invertPdfColors
        this[Keys.sortOrder] = settings.sortOrder.name
        this[Keys.tagFilterMode] = when (settings.tagFilterMode) {
            TagFilterMode.ALL -> TAG_FILTER_ALL
            TagFilterMode.ANY -> TAG_FILTER_ANY
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default
}
