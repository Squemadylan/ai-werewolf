package com.squemadylan.wolfcha

import android.app.Application
import com.squemadylan.wolfcha.util.VoiceHelper

class WolfchaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        VoiceHelper.init(this)
    }

    companion object {
        @Volatile
        var instance: WolfchaApplication? = null
            private set
    }
}
