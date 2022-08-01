package de.kai_morich.simple_usb_terminal.data

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import de.kai_morich.simple_usb_terminal.CustomProber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceRepository (private val activity: Activity){
    internal class DeviceDetails(val device: UsbDevice, val port: Int, val driver: UsbSerialDriver?)

    private val devices = ArrayList<DeviceDetails>()

    private var knownDevice: DeviceDetails? = null
    private val _hasKnownDevice = MutableStateFlow(false)
    val hasKnownDevice = _hasKnownDevice.asStateFlow()

    private var baudRate = 19200

    private fun refreshDeviceList() {
        knownDevice = null
        _hasKnownDevice.value = false
        val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
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
                _hasKnownDevice.value = true
            } else if(device.vendorId == 6790 && device.productId == 29987){
                // Witmotion USB adapter
                knownDevice = devices[devices.size-1]
                _hasKnownDevice.value = true
            }
        }
    }
}