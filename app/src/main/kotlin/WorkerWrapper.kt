package de.kai_morich.simple_usb_terminal

import android.content.Context
import androidx.work.*

class WorkerWrapper {
    companion object {
        @JvmStatic fun startWorker(context: Context){
            val constraints = Constraints.Builder().build()
            val request = OneTimeWorkRequestBuilder<MainCoroutineWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}