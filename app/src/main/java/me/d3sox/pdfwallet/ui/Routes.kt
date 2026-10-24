package me.d3sox.pdfwallet.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DocumentsRoute : NavKey

@Serializable
data class DetailsRoute(val documentId: String) : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object AboutRoute : NavKey
