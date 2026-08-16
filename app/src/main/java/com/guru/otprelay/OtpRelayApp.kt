package com.guru.otprelay

import android.app.Application
import com.guru.otprelay.data.Store

class OtpRelayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
