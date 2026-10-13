package com.Consnow.features.steps.data.sensor

import android.content.BroadcastReceiver //listen for screen off and on
import android.content.Context
import android.content.Intent

class PhoneUnlockReceiver(
    private val onUnlock: () -> Unit, //
    private val onLock: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> onUnlock() //called when user unlocks the phone executes onunlock
            Intent.ACTION_SCREEN_OFF -> onLock() // called when screen is off
        }
    }
}