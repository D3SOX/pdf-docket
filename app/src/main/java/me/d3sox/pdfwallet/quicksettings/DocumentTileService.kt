package me.d3sox.pdfwallet.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import me.d3sox.pdfwallet.MainActivity
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.documents.openDocumentIntent
import me.d3sox.pdfwallet.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A Quick Settings tile that opens the document picked in its menu, or the app without one.
 */
@AndroidEntryPoint
class DocumentTileService : TileService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var documentRepository: DocumentRepository

    private val scope = MainScope()

    override fun onStartListening() {
        scope.launch {
            val target = target()
            val tile = qsTile ?: return@launch
            tile.state = if (target.name != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = target.visibleName ?: getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = when {
                    target.name == null -> getString(R.string.tile_no_document)
                    target.visibleName != null -> getString(R.string.app_name)
                    else -> null
                }
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        scope.launch {
            val intent = target().documentId?.let(::openDocumentIntent)
                ?: Intent(this@DocumentTileService, MainActivity::class.java)
            if (isLocked) unlockAndRun { open(intent) } else open(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun target(): Target {
        val settings = settingsRepository.settings.first()
        val id = settings.quickSettingsDocumentId
        val name = id?.let { documentRepository.name(it) }
        return Target(
            documentId = id.takeIf { name != null },
            name = name,
            // the tile is also shown on the lock screen
            visibleName = name.takeUnless { settings.requireAuthentication },
        )
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** [documentId] is null if there is no document for the tile, [visibleName] while the app is locked */
    private class Target(val documentId: String?, val name: String?, val visibleName: String?)
}

/**
 * Asks the user to add the tile to Quick Settings, unless it's already there.
 * Returns false where the system can't ask, before Android 13.
 */
fun Context.requestQuickSettingsTile(label: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(this, DocumentTileService::class.java),
        label,
        Icon.createWithResource(this, R.drawable.tag_icon_confirmation_number),
        mainExecutor,
    ) {}
    return true
}
