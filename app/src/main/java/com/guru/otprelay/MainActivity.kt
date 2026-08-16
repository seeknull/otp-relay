package com.guru.otprelay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.guru.otprelay.data.Preset
import com.guru.otprelay.data.RequestLink
import com.guru.otprelay.data.Store
import com.guru.otprelay.forwarding.ForwardingService
import com.guru.otprelay.ui.MainScreen
import com.guru.otprelay.ui.OtpRelayTheme

class MainActivity : ComponentActivity() {

    private val request = mutableStateOf<Preset?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If the app was force-stopped mid-session the service died with it; bring it back so the
        // UI never claims to be forwarding while nothing is listening.
        if (Store.currentSession() != null) ForwardingService.start(this)

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
