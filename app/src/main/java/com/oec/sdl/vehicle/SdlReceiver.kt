package com.oec.sdl.vehicle

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

import com.smartdevicelink.transport.SdlBroadcastReceiver
import com.smartdevicelink.transport.SdlRouterService
import com.smartdevicelink.transport.TransportConstants

class SdlReceiver : SdlBroadcastReceiver() {

    override fun onSdlEnabled(context: Context, intent: Intent) {
        Log.d(TAG, "SDL Enabled")
        intent.setClass(context, SdlService::class.java)

        // SdlService needs to be foregrounded in Android O and above
        // This will prevent apps in the background from crashing when they try to start SdlService
        // Because Android O doesn't allow background apps to start background services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun defineLocalSdlRouterClass(): Class<out SdlRouterService> {
        return SdlRouterService::class.java
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Required if overriding this method

        if (intent != null) {
            val action = intent.action
            if (action != null) {
                if (action.equals(TransportConstants.START_ROUTER_SERVICE_ACTION, ignoreCase = true)) {
                    if (intent.getBooleanExtra(RECONNECT_LANG_CHANGE, false)) {
                        onSdlEnabled(context, intent)
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = "SdlBroadcastReciever"
        val RECONNECT_LANG_CHANGE = "RECONNECT_LANG_CHANGE"
    }
}