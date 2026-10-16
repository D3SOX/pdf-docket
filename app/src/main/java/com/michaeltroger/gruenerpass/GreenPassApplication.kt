package com.michaeltroger.gruenerpass

import android.app.Application
import android.content.ComponentCallbacks2
import com.michaeltroger.gruenerpass.render.BitmapCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GreenPassApplication : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            BitmapCache.memoryCache.evictAll()
        }
    }
}
