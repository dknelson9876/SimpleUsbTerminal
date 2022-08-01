package de.kai_morich.simple_usb_terminal

import androidx.lifecycle.ViewModel
import de.kai_morich.simple_usb_terminal.data.DeviceRepository

class DevicesViewModel constructor(
    deviceRepository: DeviceRepository
) : ViewModel() {

    val hasKnownDevice = deviceRepository.hasKnownDevice

    fun refreshDeviceList(){
    }
}