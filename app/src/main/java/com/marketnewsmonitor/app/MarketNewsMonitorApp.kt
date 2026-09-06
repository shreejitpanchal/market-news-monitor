package com.marketnewsmonitor.app

import android.app.Application

class MarketNewsMonitorApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
