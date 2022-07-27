package de.kai_morich.simple_usb_terminal.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * A wrapper class that makes it easier to start a given service
 * */
class WorkerWrapper {
    companion object {
        @JvmStatic fun startFirebaseWorker(context: Context){
            val constraints = Constraints.Builder().build()
            val request = OneTimeWorkRequestBuilder<FirebaseWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
        @JvmStatic fun startSerialWorker(context: Context){
            val constraints = Constraints.Builder().build()
            val request = OneTimeWorkRequestBuilder<SerialWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

    }
}