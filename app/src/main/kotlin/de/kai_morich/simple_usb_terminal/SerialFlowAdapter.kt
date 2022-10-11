package de.kai_morich.simple_usb_terminal

import java.lang.Exception

class SerialFlowAdapter {

    companion object {
        @JvmStatic
        fun onSerialConnectError(e: Exception) {
            TODO("Not yet implemented")
        }

         fun onSerialConnect() {
            TODO("Not yet implemented")
        }

         fun onSerialRead(data: ByteArray?) {
            TODO("Not yet implemented")
        }

         fun onSerialIoError(e: Exception?) {
            TODO("Not yet implemented")
        }
    }


}