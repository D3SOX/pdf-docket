package me.d3sox.pdfwallet.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import me.d3sox.pdfwallet.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DocumentWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var widgets: DocumentWidgets

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(appWidgetIds)
    }

    // the preview is cropped to the widget's shape
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        update(intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgets.remove(appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        widgets.restore(oldWidgetIds, newWidgetIds)
    }

    private fun update(widgetIds: IntArray) {
        // rendering the previews takes a while
        val result = goAsync()
        scope.launch {
            try {
                widgets.update(widgetIds)
            } finally {
                result.finish()
            }
        }
    }
}
