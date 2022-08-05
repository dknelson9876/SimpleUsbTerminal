package de.kai_morich.simple_usb_terminal

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.*
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.*
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.Priority
import com.hoho.android.usbserial.driver.SerialTimeoutException
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import de.kai_morich.simple_usb_terminal.FirebaseService.Companion.instance
import de.kai_morich.simple_usb_terminal.SerialService.SerialBinder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The UI portion of the app that is displayed while connected to a USB serial device
 * There's a lot of non-UI logic still in here that needs to be cleaned up and/or removed
 * entirely as we won't actually use it
 */
@RequiresApi(api = Build.VERSION_CODES.O)
class TerminalFragment : Fragment(), ServiceConnection, SerialListener {
    private fun onSetupClicked() {
        send(BGapi.SCANNER_SET_MODE)
        send(BGapi.SCANNER_SET_TIMING)
        send(BGapi.CONNECTION_SET_PARAMETERS)
    }

    private fun onStartClicked() {
        send(BGapi.SCANNER_START)
    }

    private enum class Connected {
        False, Pending, True
    }

    private val broadcastReceiver: BroadcastReceiver
    private var deviceId = 0
    private var portNum = 0
    private var baudRate = 0
    private var usbSerialPort: UsbSerialPort? = null
    private var service: SerialService? = null
    private var receiveText: TextView? = null
    private var pendingPacket: BlePacket? = null
    private var connected = Connected.False
    private var initialStart = true
    private var truncate = true
    private val newline = TextUtil.newline_crlf
    private var rotatePeriod = 500

    //region Lifecycle
    /**
     * Inherited from Fragment. One of the first methods that the system will call
     * after the constructor. Retrieves the information about the device to connect to
     * that was sent over by the DevicesFragment
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        deviceId = requireArguments().getInt("device")
        portNum = requireArguments().getInt("port")
        baudRate = requireArguments().getInt("baud")
    }

    /**
     * Inherited from Fragment. Called by the system when the app gets closed
     */
    override fun onDestroy() {
        if (connected != Connected.False) disconnect()
        requireActivity().stopService(Intent(activity, SerialService::class.java))
        super.onDestroy()
    }

    /**
     * Inherited from Fragment. Called by the system
     */
    override fun onStart() {
        super.onStart()
        if (service != null)
            service!!.attach(this)
        else
            requireActivity().startService(Intent(activity, SerialService::class.java)) // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    /**
     * Inherited from Fragment. Called by the system. Unsubscribes from messages from the serial device
     * as this Fragment is no longer being displayed
     */
    override fun onStop() {
        if (service != null && !requireActivity().isChangingConfigurations) service!!.detach()
        super.onStop()
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        requireActivity().bindService(
            Intent(getActivity(), SerialService::class.java),
            this,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDetach() {
        try {
            requireActivity().unbindService(this)
        } catch (ignored: Exception) {
        }
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().registerReceiver(broadcastReceiver, IntentFilter(Constants.INTENT_ACTION_GRANT_USB))
        if (initialStart && service != null) {
            initialStart = false
            requireActivity().runOnUiThread { connect() }
        }
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as SerialBinder).service
        service!!.attach(this)
        if (initialStart && isResumed) {
            initialStart = false
            requireActivity().runOnUiThread { connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText =
            view.findViewById(R.id.receive_text) // TextView performance decreases with number of spans
        receiveText!!.setTextColor(resources.getColor(R.color.colorRecieveText)) // set as default color to reduce number of spans
        receiveText!!.movementMethod = ScrollingMovementMethod.getInstance()
        val setupBtn = view.findViewById<View>(R.id.setup_btn)
        setupBtn.setOnClickListener { onSetupClicked() }
        val stopUploadBtn = view.findViewById<View>(R.id.stop_upload_btn)
        stopUploadBtn.setOnClickListener {
            Toast.makeText(context, "click!", Toast.LENGTH_SHORT).show()
            val stopIntent = Intent(context, FirebaseService.ActionListener::class.java)
            stopIntent.action = FirebaseService.KEY_NOTIFICATION_STOP_ACTION
            stopIntent.putExtra(
                FirebaseService.KEY_NOTIFICATION_ID,
                ServiceNotification.notificationId
            )
            instance!!.sendBroadcast(stopIntent)
        }
        val stopMotorBtn = view.findViewById<SwitchCompat>(R.id.stop_motor_btn)
        stopMotorBtn.setOnCheckedChangeListener { _, isChecked ->
            val stopMotorIntent = Intent(context, SerialService.ActionListener::class.java)
            stopMotorIntent.action = SerialService.KEY_STOP_MOTOR_ACTION
            stopMotorIntent.putExtra(SerialService.KEY_MOTOR_SWITCH_STATE, isChecked)
            SerialService.getInstance().sendBroadcast(stopMotorIntent)
        }
        val startBtn = view.findViewById<View>(R.id.start_btn)
        startBtn.setOnClickListener { onStartClicked() }

        val stopBtn = view.findViewById<View>(R.id.stop_btn)
        stopBtn.setOnClickListener { send(BGapi.SCANNER_STOP) }

        val gpsPriority = view.findViewById<Spinner>(R.id.gps_priority_spinner)
        val gpsOptions = arrayOf("Power Saving", "Balanced", "High Accuracy")
        val adapter = ArrayAdapter<CharSequence>(requireContext(), android.R.layout.simple_spinner_item, gpsOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gpsPriority.adapter = adapter
        gpsPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                val activity: Activity? = activity
                if (activity is MainActivity) {
                    when (position) {
                        0 -> activity.updateLocationPriority(Priority.PRIORITY_LOW_POWER)
                        1 -> activity.updateLocationPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        2 -> activity.updateLocationPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                //do nothing
            }
        }

        //TODO switch to get the filename directly from FirebaseService
        receiveText!!.append("Writing to ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss"))}_log.txt")

        return view
    }

    /**
     * Inherited from Fragment. The Options menu is the 3 dots in the top right corner
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
        menu.findItem(R.id.truncate).isChecked = truncate
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear -> {
                receiveText!!.text = ""
                true
            }
            R.id.manualUpload -> {
                instance!!.uploadLog()
                true
            }
            R.id.truncate -> {
                truncate = !truncate
                item.isChecked = truncate
                true
            }
            R.id.manualCW -> {
                send(BGapi.ROTATE_CW)
                SystemClock.sleep(500)
                send(BGapi.ROTATE_STOP)
                true
            }
            R.id.manualCCW -> {
                send(BGapi.ROTATE_CCW)
                SystemClock.sleep(500)
                send(BGapi.ROTATE_STOP)
                true
            }
            R.id.editRotate -> {
                //TODO actually change the period in SerialService
                val builder = AlertDialog.Builder(context)
                builder.setTitle("New Rotation Period UNUSED")
                val input = EditText(context)
                input.inputType = InputType.TYPE_CLASS_TEXT
                builder.setView(input)
                builder.setPositiveButton("OK") { _: DialogInterface?, _: Int ->
                    rotatePeriod = input.text.toString().toInt()
                    Toast.makeText(context, "Set rotation period to $rotatePeriod", Toast.LENGTH_SHORT).show()
                }
                builder.setNegativeButton("Cancel") { dialog: DialogInterface, _: Int -> dialog.cancel() }
                builder.show()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }


    // endregion Lifecycle

    //region Serial

    /**
     * Do all the listing and check required to connect to the USB device using the details
     * that were passed when this Fragment was started
     * But some of this seems like duplicate logic from DevicesFragment, so this might be able
     * to be reduced
     */
    private fun connect(permissionGranted: Boolean? = null) {
        var device: UsbDevice? = null
        val usbManager = requireActivity().getSystemService(Context.USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) if (v.deviceId == deviceId) device = v
        if (device == null) {
            status("connection failed: device not found")
            return
        }
        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device)
        }
        if (driver == null) {
            status("connection failed: no driver for device")
            return
        }
        if (driver.ports.size < portNum) {
            status("connection failed: not enough ports at device")
            return
        }
        //TODO: Non-UI logic - should not be in a UI class
        usbSerialPort = driver.ports[portNum]
        val usbConnection = usbManager.openDevice(driver.device)
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.device)) {
            val usbPermissionIntent = PendingIntent.getBroadcast(
                activity,
                0,
                Intent(Constants.INTENT_ACTION_GRANT_USB),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.device))
                status("connection failed: permission denied")
            else
                status("connection failed: open failed")
            return
        }
        connected = Connected.Pending
        try {
            usbSerialPort!!.open(usbConnection)
            usbSerialPort!!.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            val socket = SerialSocket(requireActivity().applicationContext, usbConnection, usbSerialPort)
            service!!.connect(socket)
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect()
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        connected = Connected.False
        service!!.disconnect()
        usbSerialPort = null
    }

    /**
     * Send a String to the currently connected serial device. Returns immediately if no
     * device is connected. Additionally appends the sent information to the text on screen
     */
    private fun send(str: String?) {
        if (connected != Connected.True) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            //TODO: Non-UI logic - should not be in UI class
            var msg: String
            val sb = StringBuilder()
            TextUtil.toHexString(sb, TextUtil.fromHexString(str))
            TextUtil.toHexString(sb, newline.toByteArray())
            msg = sb.toString()
            val data: ByteArray = TextUtil.fromHexString(msg)
            if (BGapi.isCommand(str!!)) {
                msg = BGapi.getCommandName(str)!!
            }
            val spn = SpannableStringBuilder(msg)
            spn.setSpan(
                ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            receiveText!!.append(spn)
            service!!.write(data)
        } catch (e: SerialTimeoutException) {
            status("write timeout: " + e.message)
        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }

    /**
     * Parse the bytes that were received from the serial device. If those bytes are recognized
     * as a message that is part of BGAPI, prints the message name rather than the bytes
     * If the message is a packet, parse it into a packet object
     */
    private fun receive(data: ByteArray) {
        if (BGapi.isScanReportEvent(data)) {
            //original script recorded time, addr, rssi, channel, and data
            //TODO: Non-UI logic - should not be in UI class
            if (pendingPacket != null) {
                var msg = pendingPacket.toString()
                if (truncate) {
                    var length = msg.length
                    if (length > msg.lastIndexOf('\n') + 40) {
                        length = msg.lastIndexOf('\n') + 40
                    }
                    msg = msg.substring(0, length) + "..."
                }
                val spn = SpannableStringBuilder(msg)
                spn.setSpan(
                    ForegroundColorSpan(Color.MAGENTA),
                    0,
                    spn.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                receiveText!!.append(spn)
            }
            if (data.size <= 21) return
            pendingPacket = BlePacket.parsePacket(data)
        } else if (BGapi.isKnownResponse(data)) {
            val rsp = BGapi.getResponseName(data)
            if (rsp != null && !rsp.contains("rotate"))
                receiveText!!.append(BGapi.getResponseName(data))
        } else {
            //until the data has a terminator, assume packets that aren't a known header are data that was truncated
            if (pendingPacket != null) pendingPacket!!.appendData(data)
        }

        //If the text in receiveText is getting too large to be reasonable, cut it off
        if (receiveText!!.text.length > 8000) {
            val text = receiveText!!.text
            val length = text.length
            receiveText!!.text = text.subSequence(length - 2000, length)
        }
    }

    /**
     * Print to the textview in a different color so that it stands out
     */
    fun status(str: String) {
        val spn = SpannableStringBuilder(str)
        spn.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorStatusText)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        receiveText!!.append(spn)
    }

    //endregion

    //region SerialListener

    override fun onSerialConnect() {
        status("connected")
        connected = Connected.True
        //send setup and start commands after delay via custom Handler
        val handler = Handler()
        val clickSetup = Runnable { onSetupClicked() }
        handler.postDelayed(clickSetup, 2500)
        val clickStart = Runnable { onStartClicked() }
        handler.postDelayed(clickStart, 2700)
    }

    override fun onSerialConnectError(e: Exception) {
        status("connection failed: " + e.message)
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        receive(data)
    }

    override fun onSerialIoError(e: Exception) {
        status("connection lost: " + e.message)
        status(Log.getStackTraceString(e))
        disconnect()
    }

    init {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Constants.INTENT_ACTION_GRANT_USB == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    connect(granted)
                }
            }
        }
    }
}