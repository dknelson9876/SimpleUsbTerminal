package de.kai_morich.simple_usb_terminal

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainCoroutineWorker (private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams){

    companion object {
        private var TAG = this::class.simpleName
        private var NOTIFICATION_ID = 3102
    }

    override suspend fun doWork(): Result {
        if(SerialService.getInstance() == null){
            withContext(Dispatchers.IO){
                val trackerServiceIntent = Intent(context, SerialService::class.java)
                ServiceNotification.notificationText = "do not close the app, please"
                ServiceNotification.notificationIcon = R.mipmap.ic_launcher
                Log.i(TAG, "Launching tracker")
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    context.startForegroundService(trackerServiceIntent)
                } else {
                    context.startService(trackerServiceIntent)
                }
            }
        }

        return Result.success()
    }
}