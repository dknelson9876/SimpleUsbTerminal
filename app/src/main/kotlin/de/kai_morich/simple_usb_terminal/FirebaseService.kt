package de.kai_morich.simple_usb_terminal

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
class FirebaseService : Service() {
    private var currentNotification: ServiceNotification? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var handler: Handler? = null
    private var fw: FileWriter? = null
    private var file: File? = null
    private lateinit var timeoutRunnable: Runnable

    companion object {
        private val TAG = FirebaseService::class.java.simpleName
        var instance: FirebaseService? = null
        private const val NOTIFICATION_ID = 3956
    }

    override fun onCreate() {
        super.onCreate()
        instance = this


        var path = applicationContext.getExternalFilesDir(null)
        file = File(
            path,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                    + "+log.txt"
        )
        fw = FileWriter(file)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "starting foreground service")
        startWakeLock()
        try {
            Log.i(TAG, "starting foreground process")
            currentNotification = ServiceNotification(this, NOTIFICATION_ID, false)
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                startForeground(NOTIFICATION_ID, currentNotification!!.notification!!, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
//            } else {
            startForeground(NOTIFICATION_ID, currentNotification!!.notification)
//            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground process " + e.message)
        }
        startHandler()
        return START_REDELIVER_INTENT
    }

    @SuppressLint("WakelockTimeout")
    /* Studio is mad that we don't provide a timeout, but
     *    in this case it's on purpose
     */
    private fun startWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        wakeLock?.acquire()
    }

    private fun startHandler() {
        Log.i(TAG, "Starting handler")
        val looper = Looper.myLooper()
        looper.let {
            handler = Handler(looper!!)
            timeoutRunnable = Runnable {
                uploadLog()
//                uploadFile()
                handler?.postDelayed(timeoutRunnable,
//                    120000 /*2 minutes*/
                    60000 /*1 minute*/
                )
            }
            handler?.postDelayed(timeoutRunnable, 60000)
        }
//        handler = Handler(looper!!)
//        timeoutRunnable = Runnable { uploadFile() }
//        handler!!.post(timeoutRunnable!!)
    }

    private fun stopHandler() {
        Log.i(TAG, "stopping handler")
        try {
            handler!!.looper.quitSafely()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop handler " + e.message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "FirebaseService#onDestroy")
        stopHandler()
        if (wakeLock != null) wakeLock!!.release()
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "onBind")
        return null
    }

    fun uploadFile(file: File) {
        Log.i(TAG, "FirebaseService#uploadFile()")
        val storageRef = FirebaseStorage.getInstance().reference
        val uri = Uri.fromFile(file)
        val fileRef = storageRef.child(
            "log/"
                    + Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
                    + "/" + uri.lastPathSegment
        )
        fileRef.putFile(uri)
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Upload Success", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(applicationContext, it.message, Toast.LENGTH_SHORT).show()
            }
    }

    fun testUpload(origin: String) {
        Log.i(TAG, "FirebaseService#testUpload()")
        val storageRef = FirebaseStorage.getInstance().reference
        val path = getExternalFilesDir(null)
        val file = File(path, "file.txt")
        try {
            val fw = FileWriter(file)
            fw.write("some text")
            fw.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val fileRef = storageRef.child(
            "test/"
                    + Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
                    + "/"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                    + "_"
                    + origin
                    + ".txt"
        )
        fileRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                Toast.makeText(
                    applicationContext, "Upload Success", Toast.LENGTH_SHORT
                ).show()
            }
    }

    fun appendFile(csv: String) {
        fw?.write(csv);
    }

    fun uploadLog() {
        //close the FileWriter
        fw?.close()

        //upload the log
        Toast.makeText(applicationContext, "File status: " + (file != null), Toast.LENGTH_SHORT)
            .show();
        file?.let { uploadFile(it) }

        //create new File + FileWriter
        var path = applicationContext.getExternalFilesDir(null)
        file = File(
            path,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                    + "_log.txt"
        )
        fw = FileWriter(file)
    }


}