package com.Consnow.features.steps.data.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.Consnow.ConsnowApp
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart step counting if user was logged in
            val app = context.applicationContext as ConsnowApp
            val isLoggedIn = runBlocking { app.appContainer.tokenManager.getToken() != null }
            if (isLoggedIn) {
                // Reboot carry-forward of today's steps is handled in StepCounterService.onSensorChanged
                StepCounterService.start(context)
            }
        }
    }
}
