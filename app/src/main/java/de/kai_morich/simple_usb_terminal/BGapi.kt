package de.kai_morich.simple_usb_terminal

import de.kai_morich.simple_usb_terminal.BGapi
import java.util.*

/**
 * A wrapper class for the constants we use to communicate with the Gecko.
 *
 * While these technically should be findable in Silicon Lab's documentation, it's a lot easier
 * to open up the NCP Commander in Simplicity Studio, manually send the commands there, and turn
 * on the option to display the bytes that were sent
 */
class BGapi {





    companion object {
        private val knownResponses = HashMap<String, ByteArray>()
        private val commands = HashMap<String, String>()

        @JvmStatic
        val SCANNER_SET_MODE = "200205020401"
        @JvmStatic
        val SCANNER_SET_TIMING = "200505010410001000"
        @JvmStatic
        val CONNECTION_SET_PARAMETERS = "200c060050005000000064000000ffff"
        @JvmStatic
        val SCANNER_START = "200205030402"
        @JvmStatic
        val SCANNER_STOP = "20000505"
        @JvmStatic
        val ROTATE_CW = "2002FF000101"
        @JvmStatic
        val ROTATE_CCW = "2002FF000102"
        @JvmStatic
        val ROTATE_STOP = "2002FF000103"

        init {
            knownResponses["scanner_set_mode_rsp"] = byteArrayOf(0x20, 0x02, 0x05, 0x02, 0x00, 0x00)
            knownResponses["scanner_set_timing_rsp"] =
                byteArrayOf(0x20, 0x02, 0x05, 0x01, 0x00, 0x00)
            knownResponses["connection_set_default_parameters_rsp"] =
                byteArrayOf(0x20, 0x02, 0x06, 0x00, 0x00, 0x00)
            knownResponses["scanner_start_rsp"] = byteArrayOf(0x20, 0x02, 0x05, 0x03, 0x00, 0x00)
            knownResponses["scanner_stop_rsp"] = byteArrayOf(0x20, 0x02, 0x05, 0x05, 0x00, 0x00)
            knownResponses["message_rotate_cw_rsp"] =
                byteArrayOf(0x20, 0x04, 0xFF.toByte(), 0x00, 0x00, 0x00, 0x01, 0x01)
            knownResponses["message_rotate_ccw_rsp"] =
                byteArrayOf(0x20, 0x04, 0xFF.toByte(), 0x00, 0x00, 0x00, 0x01, 0x02)
            knownResponses["message_rotate_stop_rsp"] =
                byteArrayOf(0x20, 0x04, 0xFF.toByte(), 0x00, 0x00, 0x00, 0x01, 0x03)

            commands["scanner_set_mode"] = "200205020401"
            commands["scanner_set_timing"] = "200505010410001000"
            commands["connection_set_parameters"] = "200c060050005000000064000000ffff"
            commands["scanner_start"] = "200205030402"
            commands["scanner_stop"] = "20000505"
            commands["message_rotate_cw"] = "2002FF000101"
            commands["message_rotate_ccw"] = "2002FF000102"
            commands["message_rotate_stop"] = "2002FF000103"
        }

        @JvmStatic
        fun isScanReportEvent(bytes: ByteArray): Boolean {
            // Note: the casting of 0xA1 is necessary because Java thinks that signed hex should exist?
            //      and as a result does funny things
            return bytes.size > 3
                    && bytes[0] == 0xA1.toByte()
                    && bytes[1] == 0x00.toByte()
                    && bytes[2] == 0x05.toByte()
                    && bytes[3] == 0x01.toByte()
        }

        @JvmStatic
        fun isKnownResponse(bytes: ByteArray?): Boolean {
            for (response in knownResponses.values) {
                if (Arrays.equals(response, bytes)) return true
            }
            return false
        }

        @JvmStatic
        fun getResponseName(bytes: ByteArray?): String? {
            for ((key, value) in knownResponses) {
                if (Arrays.equals(value, bytes)) {
                    return key
                }
            }
            return null
        }

        fun getCommandValue(msg: String): String? {
            for ((key, value) in commands) {
                if (key == msg) {
                    return value
                }
            }
            return null
        }

        @JvmStatic
        fun getCommandName(msg: String): String? {
            for ((key, value) in commands) {
                if (value == msg) {
                    return key
                }
            }
            return null
        }

        @JvmStatic
        fun isCommand(msg: String): Boolean {
            for ((_, value) in commands) {
                if (value == msg) {
                    return true
                }
            }
            return false
        }


    }
}