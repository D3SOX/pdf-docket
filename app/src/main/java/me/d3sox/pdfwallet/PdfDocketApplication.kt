package me.d3sox.pdfwallet

import android.app.Application
import android.content.ComponentCallbacks2
import me.d3sox.pdfwallet.di.ApplicationScope
import me.d3sox.pdfwallet.reminders.ArchiveReminders
import me.d3sox.pdfwallet.render.BitmapCache
import me.d3sox.pdfwallet.widget.DocumentWidgets
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PdfDocketApplication : Application() {

    @Inject
    lateinit var widgets: DocumentWidgets

    @Inject
    lateinit var archiveReminders: ArchiveReminders

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scope.launch { widgets.updateOnChanges() }
        scope.launch { archiveReminders.scheduleOnChanges() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            BitmapCache.evictAll()
        }
    }
}
