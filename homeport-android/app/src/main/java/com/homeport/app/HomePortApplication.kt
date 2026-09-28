package com.homeport.app

import android.app.Application
import com.homeport.app.network.AndroidMeshServerManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HomePortApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            AndroidMeshServerManager.getInstance(this).startServer()
            com.homeport.app.util.SoundManager.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
