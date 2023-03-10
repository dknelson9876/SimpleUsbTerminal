package de.kai_morich.simple_usb_terminal

/**
 * A wrapper class for the constants we use to communicate with the Gecko.
 *
 * While these technically should be findable in Silicon Lab's documentation, it's a lot easier
 *  to open up the NCP Commander in Simplicity Studio, manually send the commands there, and turn
 *  on the option to display the bytes that were sent
 * */
class BGapi {
    enum class Response (val bytes: ByteArray) {
        SCANNER_SET_MODE(byteArrayOf(0x20, 0x02, 0x05, 0x02, 0x00, 0x00)),
        SCANNER_SET_TIMING(byteArrayOf(0x20, 0x02, 0x05, 0x01, 0x00, 0x00)),
        CONNECTION_SET_DEFAULT_PARAMETERS(byteArrayOf(0x20, 0x02, 0x06, 0x00, 0x00, 0x00)),
        SCANNER_START(byteArrayOf(0x20, 0x02, 0x05, 0x03, 0x00, 0x00)),
        SCANNER_START_FAILED(byteArrayOf(0x20, 0x02, 0x05, 0x03, 0x02, 0x00)),
        SCANNER_STOP(byteArrayOf(0x20, 0x02, 0x05, 0x05, 0x00, 0x00)),
        MESSAGE_ROTATE_CW(byteArrayOf(0x20, 0x04, 0xFF.toByte(), 0x00, 0x00, 0x00, 0x01, 0x01)),
        MESSAGE_ROTATE_CCW(byteArrayOf(0x20, 0x04, 0xFF.toByte(), 0x00, 0x00, 0x00, 0x01, 0x02)),
        MESSAGE_ROTATE_STOP(byteArrayOf(0x20, 0x04, 0xFF.toByte(), 0x00, 0x00, 0x00, 0x01, 0x03)),
        MESSAGE_ROTATE_RELATED(byteArrayOf(0x20, 0x03, 0xFF.toByte(), 0x00, 0x01, 0x00, 0x00)),
        SYSTEM_BOOT(byteArrayOf(
            0xA0.toByte(), 0x12, 0x01, 0x00, 0x03, 0x00, 0x03, 0x00, 0x02, 0x00,
            0x96.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0xE0.toByte(), 0x5C, 0x35, 0x51))
        ;

        companion object {
            @JvmStatic
            fun getResponseName(bytes: ByteArray): String? {
                return values().find {it.bytes.contentEquals(bytes)}?.name
            }

            @JvmStatic
            fun isKnownResponse(bytes: ByteArray): Boolean {
                return values().any {it.bytes.contentEquals(bytes)}
            }
        }
    }

    enum class Command (val value: String) {
        SCANNER_SET_MODE("200205020401"),
        SCANNER_SET_TIMING("200505010410001000"),
        CONNECTION_SET_PARAMETERS("200c060050005000000064000000ffff"),
        SCANNER_START("200205030402"),
        SCANNER_STOP("20000505"),
        ROTATE_CW("2002FF000101"),
        ROTATE_CCW("2002FF000102"),
        ROTATE_STOP("2002FF000103"),
        GET_TEMP("2002FF000104");

        companion object {
            @JvmStatic
            fun getCommandName(msg: String): String? {
                return values().find {it.value.contentEquals(msg)}?.name
            }

            @JvmStatic
            fun isCommand(msg: String): Boolean {
                return values().any {it.value.contentEquals(msg)}
            }
        }

    }

    companion object {
        @JvmStatic
        fun isScanReportEvent(byteArray: ByteArray): Boolean {
            val magicBytes = byteArrayOf(0xA0.toByte(), 0x1B.toByte(), 0x05.toByte(), 0x02.toByte())
            return byteArray.size >= magicBytes.size && byteArray.copyOfRange(0, magicBytes.size).
                contentEquals(magicBytes)
        }

        @JvmStatic
        fun isTemperatureResponse(bytes: ByteArray): Boolean {
            return bytes.size == 9 && bytes.last() == 0x69.toByte()
        }

    }


}