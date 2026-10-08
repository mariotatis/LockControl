package com.mariotatis.lockcontrol

import android.app.Application
import com.mariotatis.lockcontrol.data.ConfigRepository

class LockControlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ConfigRepository.init(this)
    }
}
