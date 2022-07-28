package de.kai_morich.simple_usb_terminal.ui

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.text.set
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.hoho.android.usbserial.driver.SerialTimeoutException
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import de.kai_morich.simple_usb_terminal.*
import java.lang.Exception

class TerminalViewModel(
    private val state: SavedStateHandle,
    private val serviceConnection: SerialServiceConnection
    ) : ViewModel(), ServiceConnection by serviceConnection {

    private val _terminalText = MutableLiveData<String>()
    val terminalText: LiveData<String>
        get() = _terminalText

    //TODO: A DeviceState class might make sense as a wrapper to these values
    private val deviceId: Int? = state["device"]
    private val portNum: Int? = state["port"]
    private val baudRate: Int? = state["baud"]

    private val serialListener: SerialListener = TerminalSerialListener()
    private lateinit var usbSerialPort: UsbSerialPort

    private var pendingPacket: BlePacket? = null

    enum class Connected {False, Pending, True}

    fun attach(): Boolean {
        return if(serviceConnection.service != null){
            serviceConnection.service?.attach((serialListener))
            true
        } else {
            false
        }
    }

    fun detach() {
        serviceConnection.service?.detach()
    }

    fun connect(usbManager: UsbManager) = connect(null)

    fun connect(permissionGranted: Boolean?, usbManager: UsbManager){
        var device: UsbDevice? = null
        for(v: UsbDevice in usbManager.deviceList.values)
            if(v.deviceId == deviceId)
                device = v
        if(device == null) {
            status("connection failed: device not found")
            return
        }
        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if(driver == null){
            driver = CustomProber.getCustomProber().probeDevice(device)
        }
        if(driver == null){
            status("connection failed: no driver for device")
            return
        }
        if(driver.ports.size < portNum!!) {
            status("connection failed: not enough ports at device")
            return
        }

        usbSerialPort = driver.ports[portNum]
        val usbConnection = usbManager.openDevice(driver.device)
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.device)) {
            val usbPermissionIntent = PendingIntent.getBroadcast(
                activity,
                0,
                Intent(Constants.INTENT_ACTION_GRANT_USB),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            return
        }
        if(usbConnection == null){
            if(!usbManager.hasPermission(driver.device))
                status("connection failed: permission denied")
            else
                status("connection failed: open failed")
            return
        }

        connected = Connected.Pending
        try {
            usbSerialPort.open(usbConnection)
            usbSerialPort.setParameters(baudRate!!, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            val socket = SerialSocket(activity.applicationContext, usbConnection, usbSerialPort)
            serviceConnection.service?.connect(socket)
            onSerialConnect()
        } catch (e: Exception) {
            onSerialConnectError(e)
        }

    }

    fun disconnect() {
        connected = Connected.False
        serviceConnection.service?.disconnect()
        usbSerialPort = null
    }

    fun send(str: String){
        if(connected != Connected.True){
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sb = StringBuilder()
            TextUtil.toHexString(sb, TextUtil.fromHexString(str))
            TextUtil.toHexString(sb, newline.getBytes())
            var msg = sb.toString()
            var data: ByteArray = TextUtil.fromHexString(msg)
            if(BGapi.isCommand(str)){
                msg = BGapi.getCommandName(str)
            }
            val spn = SpannableStringBuilder(msg + "\n")
            spn.setSpan(ForegroundColorSpan(getResources.getColor(R.color.colorSendText)), 0, spn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            _terminalText.value += spn
            serviceConnection.service?.write(data)
        } catch (e: SerialTimeoutException){
            status("write timeout: ${e.message}")
        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }

    fun receive(data: ByteArray){
        if(BGapi.isScanReportEvent(data)) {
            if(pendingPacket != null){
                var msg = pendingPacket.toString()
                if(truncate) {
                    var length = msg.length
                    if(length > msg.lastIndexOf('\n') + 40 ){
                        length = msg.lastIndexOf('\n') + 40
                    }
                    msg = msg.substring(0, length) + "..."
                }
                val spn = SpannableStringBuilder(msg + "\n\n")
                spn.setSpan(ForegroundColorSpan(Color.MAGENTA), 0, spn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                _terminalText.value += spn
            }
            if(data.size <= 21)
                return

            pendingPacket = BlePacket.parsePacket(data)
        } else if(BGapi.isKnownResponse(data)) {
            val rsp = BGapi.getResponseName(data)
            if(rsp != null && !rsp.contains("rotate"))
                _terminalText.value += rsp + "\n"
        } else {
            //until that data has a terminator, assume packets that aren't a known header are incomplete packets
            if(pendingPacket != null)
                pendingPacket!!.appendData(data)
        }

        //if terminalText is getting too large to be reasonable, cut if off
        if(_terminalText.value!!.length > 8000) {
            val text = _terminalText.value!!
            val length = text.length
            _terminalText.value = text.substring(length-2000, length)
        }
    }

    fun status(str: String){
        //TODO: make this work
    }
}

//TODO: this part definitely needs to be rewritten - should be using a repository as a single source of truth
class TerminalSerialListener : SerialListener {
    override fun onSerialConnect() {
        TODO("Not yet implemented")
    }

    override fun onSerialConnectError(e: Exception?) {
        TODO("Not yet implemented")
    }

    override fun onSerialRead(data: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun onSerialIoError(e: Exception?) {
        TODO("Not yet implemented")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
//TODO: this really should go in SerialService, this is temporary
class SerialServiceConnection : ServiceConnection {

    var service: SerialService? = null
        private set

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as SerialService.SerialBinder).service
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

}