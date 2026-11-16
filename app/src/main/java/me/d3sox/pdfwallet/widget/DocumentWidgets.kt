package me.d3sox.pdfwallet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.scale
import me.d3sox.pdfwallet.MainActivity
import me.d3sox.pdfwallet.R
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.documents.openDocumentIntent
import me.d3sox.pdfwallet.render.DocumentRenderer
import me.d3sox.pdfwallet.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// the launcher keeps every widget's bitmap in memory, a smaller one would be blurry on larger widgets
private const val PREVIEW_WIDTH_PX = 384

// a line of the name with the padding around it
private const val NAME_HEIGHT_DP = 48

/**
 * The home screen widgets, each of which shows the document picked when it was added.
 */
@Singleton
class DocumentWidgets @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val documentRepository: DocumentRepository,
    private val renderer: DocumentRenderer,
    private val settingsRepository: SettingsRepository,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, DocumentWidgetProvider::class.java)
    private val updates = Mutex()

    // the document ids by widget id
    private val documentIds by lazy { context.getSharedPreferences("document_widgets", Context.MODE_PRIVATE) }

    fun isDocumentWidget(widgetId: Int): Boolean = manager.getAppWidgetInfo(widgetId)?.provider == component

    suspend fun setDocument(widgetId: Int, documentId: String) {
        withContext(dispatcher) {
            // written right away, the process may stop once the widget is added
            documentIds.edit(commit = true) { putString(widgetId.toString(), documentId) }
        }
        update(intArrayOf(widgetId))
    }

    fun remove(widgetIds: IntArray) = documentIds.edit {
        widgetIds.forEach { remove(it.toString()) }
    }

    /** e.g. after moving the app to a new device, where the widgets get new ids */
    fun restore(oldWidgetIds: IntArray, newWidgetIds: IntArray) = documentIds.edit {
        val documents = oldWidgetIds.map { documentIds.getString(it.toString(), null) }
        oldWidgetIds.forEach { remove(it.toString()) }
        newWidgetIds.zip(documents).forEach { (id, documentId) -> putString(id.toString(), documentId) }
    }

    /**
     * Updates the widgets when a document is renamed or deleted, or the app lock is turned on or off, until cancelled.
     */
    suspend fun updateOnChanges() = combine(
        documentRepository.documents.map { documents -> documents.associate { it.id to it.name } },
        settingsRepository.settings.map { it.requireAuthentication },
        ::Pair,
    )
        .distinctUntilChanged()
        // also when the app starts, it may have stopped before updating them
        .collectLatest { update(manager.getAppWidgetIds(component)) }

    // one at a time, so an update that started before the app lock was turned on can't show a preview afterwards
    suspend fun update(widgetIds: IntArray) = updates.withLock {
        val requireAuthentication = settingsRepository.settings.first().requireAuthentication
        widgetIds.forEach { widgetId ->
            // still being added, until a document is picked
            val documentId = withContext(dispatcher) { documentIds.getString(widgetId.toString(), null) }
                ?: return@forEach
            manager.updateAppWidget(widgetId, views(widgetId, documentId, requireAuthentication))
        }
    }

    private suspend fun views(widgetId: Int, documentId: String, requireAuthentication: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_document)
        val name = documentRepository.name(documentId)
        // the home screen shows the widget without unlocking the app
        val preview = if (name != null && !requireAuthentication) renderer.thumbnail(documentId) else null
        views.setTextViewText(R.id.widget_name, name ?: context.getString(R.string.shortcut_document_deleted))
        if (preview != null) views.setImageViewBitmap(R.id.widget_preview, preview.top(previewAspectRatio(widgetId)))
        views.setViewVisibility(R.id.widget_preview, if (preview != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_icon, if (preview != null) View.GONE else View.VISIBLE)
        val intent = if (name != null) {
            context.openDocumentIntent(documentId)
        } else {
            Intent(context, MainActivity::class.java)
        }
        views.setOnClickPendingIntent(
            android.R.id.background,
            // one for each widget, the intents only differ in their extras
            PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        return views
    }

    /** width by height of the preview, which leaves room for the name below it */
    private fun previewAspectRatio(widgetId: Int): Float {
        val options = manager.getAppWidgetOptions(widgetId)
        // the launcher reports the widget's size in both orientations
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(if (landscape) OPTION_APPWIDGET_MAX_WIDTH else OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(if (landscape) OPTION_APPWIDGET_MIN_HEIGHT else OPTION_APPWIDGET_MAX_HEIGHT) -
            NAME_HEIGHT_DP
        return if (width > 0 && height > 0) width.toFloat() / height else 1f
    }
}

/**
 * The top of the page in the shape of the widget's preview, like the previews in the app,
 * as widgets can only crop their images around the center.
 */
private fun Bitmap.top(aspectRatio: Float): Bitmap {
    val scaled = if (width > PREVIEW_WIDTH_PX) scale(PREVIEW_WIDTH_PX, height * PREVIEW_WIDTH_PX / width) else this
    val height = (scaled.width / aspectRatio).toInt().coerceIn(1, scaled.height)
    return Bitmap.createBitmap(scaled, 0, 0, scaled.width, height)
}
