package de.kai_morich.simple_usb_terminal.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import de.kai_morich.simple_usb_terminal.Constants
import de.kai_morich.simple_usb_terminal.CustomProber
import de.kai_morich.simple_usb_terminal.SerialSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceRepository (
    private val context: Context,
    private val onConnectError: (message: String) -> Unit
    ){
    internal class DeviceDetails(val device: UsbDevice, val port: Int, val driver: UsbSerialDriver?)

    private val devices = ArrayList<DeviceDetails>()

    private var knownDevice: DeviceDetails? = null
    private var knownDeviceDriver: UsbSerialDriver? = null
    private val _hasKnownDevice = MutableStateFlow(false)
    val hasKnownDevice = _hasKnownDevice.asStateFlow()

    private var baudRate = 19200
    private var connected = false

    private fun refreshDeviceList() {
        knownDevice = null
        _hasKnownDevice.value = false
        knownDeviceDriver = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDefaultProber = UsbSerialProber.getDefaultProber()
        val usbCustomProber = CustomProber.getCustomProber()

        devices.clear()

        for (device in usbManager.deviceList.values){
            var driver = usbDefaultProber.probeDevice(device)
            if(driver == null){
                driver = usbCustomProber.probeDevice(device)
            }
            if(driver != null){
                for(port in driver.ports.indices){
                    devices.add(DeviceDetails(device, port, driver))
                }
            } else {
                devices.add(DeviceDetails(device, 0, null))
            }

            if(device.vendorId == 4966 && device.productId == 261){
                // Silabs Gecko
                knownDevice = devices[devices.size-1]
                knownDeviceDriver = driver
                _hasKnownDevice.value = true
            } else if(device.vendorId == 6790 && device.productId == 29987){
                // Witmotion USB adapter
                knownDevice = devices[devices.size-1]
                knownDeviceDriver = driver
                _hasKnownDevice.value = true
            }
        }
    }

    fun connect(permissionGranted: Boolean? = null) {
        if(!_hasKnownDevice.value){
            onConnectError("connection failed: no known device")
            return
        }
        if(knownDeviceDriver == null){
            onConnectError("connection failed: missing device driver")
            return
        }
        if(knownDeviceDriver!!.ports.size < knownDevice?.port!!) {
            onConnectError("connection failed: not enough ports at device")
        }

        val usbSerialPort = knownDeviceDriver!!.ports[knownDevice?.port!!]
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbConnection = usbManager.openDevice(knownDeviceDriver!!.device)

        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(knownDeviceDriver!!.device)){
            val usbPermissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(Constants.INTENT_ACTION_GRANT_USB),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(knownDeviceDriver!!.device, usbPermissionIntent)
            return
        }
        if(usbConnection == null){
            if(!usbManager.hasPermission(knownDeviceDriver!!.device)){
                onConnectError("connection failed: permission denied")
            } else {
                onConnectError("connection failed: open failed")
            }
        }
        //connected = pending?
        try{
            usbSerialPort!!.open(usbConnection)
            usbSerialPort.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            val socket = SerialSocket(context, usbConnection, usbSerialPort)
            //service.connect(socket)
            //onSerialConnect()
        } catch (e: Exception) {
            //onSerialConnectError()
        }
    }
}