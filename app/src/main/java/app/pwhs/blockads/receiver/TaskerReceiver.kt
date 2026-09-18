package app.pwhs.blockads.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pwhs.blockads.service.ServiceController
import timber.log.Timber

/**
 * Receiver designed for automation apps like Tasker and MacroDroid.
 * Allows starting and stopping the Adblocker via broadcast intents.
 *
 * Actions:
 * - app.pwhs.blockads.TASKER_START
 * - app.pwhs.blockads.TASKER_STOP
 */
class TaskerReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START = "app.pwhs.blockads.TASKER_START"
        const val ACTION_STOP = "app.pwhs.blockads.TASKER_STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Timber.d("TaskerReceiver received action: $action")

        when (action) {
            ACTION_START -> {
                ServiceController.requestStart(context)
            }
            ACTION_STOP -> {
                ServiceController.requestStop(context)
            }
        }
    }
}
