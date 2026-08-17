package com.guru.otprelay

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.guru.otprelay.data.DemoData
import com.guru.otprelay.data.Preset
import com.guru.otprelay.data.RequestLink
import com.guru.otprelay.data.Store
import com.guru.otprelay.forwarding.ForwardingService
import com.guru.otprelay.ui.MainScreen
import com.guru.otprelay.ui.OtpRelayTheme

class MainActivity : FragmentActivity() {

    private val request = mutableStateOf<Preset?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If the app was force-stopped mid-session the service died with it; bring it back so the
        // UI never claims to be forwarding while nothing is listening.
        if (Store.currentSession() != null) ForwardingService.start(this)

        // Debug-only screenshot helper. Loads made-up data into memory; real data is untouched.
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("demo", false) == true) DemoData.load()

        request.value = RequestLink.parse(intent?.dataString)

        setContent {
            OtpRelayTheme {
                MainScreen(request.value) { request.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request.value = RequestLink.parse(intent.dataString)
    }
}
