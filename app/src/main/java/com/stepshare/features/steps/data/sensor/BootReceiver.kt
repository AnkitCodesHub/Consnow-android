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
                // After reboot, TYPE_STEP_COUNTER resets to 0.
                // We preserve accumulated steps in SharedPreferences.
                val prefs = context.getSharedPreferences("step_sensor_prefs", Context.MODE_PRIVATE)
                val today = java.time.LocalDate.now().toString()
                val savedDate = prefs.getString("sensor_date", null)
                if (savedDate == today) {
                    // Carry forward today's count as accumulated before reboot
                    // The service will pick it up via KEY_ACCUMULATED
                }
                StepCounterService.start(context)
            }
        }
    }
}
