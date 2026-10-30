package me.d3sox.pdfwallet

import android.app.Application
import android.content.ComponentCallbacks2
import me.d3sox.pdfwallet.render.BitmapCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GreenPassApplication : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            BitmapCache.evictAll()
        }
    }
}
