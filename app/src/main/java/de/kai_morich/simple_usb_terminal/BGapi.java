package de.kai_morich.simple_usb_terminal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BGapi {

    private static final HashMap<String, byte[]> knownResponses = new HashMap<>();
    private static final HashMap<String, String> commands = new HashMap<>();

    static {
        knownResponses.put("scanner_set_mode_rsp", new byte[]{0x20, 0x02, 0x05, 0x02, 0x00, 0x00});
        knownResponses.put("scanner_set_timing_rsp", new byte[]{0x20, 0x02, 0x05, 0x01, 0x00, 0x00});
        knownResponses.put("connection_set_default_parameters_rsp", new byte[]{0x20, 0x02, 0x06, 0x00, 0x00, 0x00});
        knownResponses.put("scanner_start_rsp", new byte[]{0x20, 0x02, 0x05, 0x03, 0x00, 0x00});
        knownResponses.put("scanner_stop_rsp", new byte[]{0x20, 0x02, 0x05, 0x05, 0x00, 0x00});
        knownResponses.put("message_rotate_cw_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x01});
        knownResponses.put("message_rotate_ccw_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x02});
        knownResponses.put("message_rotate_stop_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x03});

        commands.put("scanner_set_mode", "200205020401");
        commands.put("scanner_set_timing", "200505010410001000");
        commands.put("connection_set_parameters", "200c060050005000000064000000ffff");
        commands.put("scanner_start", "200205030402");
        commands.put("scanner_stop", "20000505");
        commands.put("message_rotate_cw", "2002FF000101");
        commands.put("message_rotate_ccw", "2002FF000102");
        commands.put("message_rotate_stop", "2002FF000103");
    }

    public static final String SCANNER_SET_MODE = commands.get("scanner_set_mode");
    public static final String SCANNER_SET_TIMING = commands.get("scanner_set_timing");
    public static final String CONNECTION_SET_PARAMETERS = commands.get("connection_set_parameters");
    public static final String SCANNER_START = commands.get("scanner_start");
    public static final String SCANNER_STOP = commands.get("scanner_stop");
    public static final String ROTATE_CW = commands.get("message_rotate_cw");
    public static final String ROTATE_CCW = commands.get("message_rotate_ccw");
    public static final String ROTATE_STOP = commands.get("message_rotate_stop");

    public static boolean isScanReportEvent(byte[] bytes) {
        // Note: the casting of 0xA1 is necessary because Java thinks that signed hex should exist?
        //      and as a result does funny things
        return bytes.length > 3 && bytes[0] == (byte) 0xA1 && bytes[1] == 0x00
                && bytes[2] == 0x05 && bytes[3] == 0x01;
    }

    public static boolean isKnownResponse(byte[] bytes) {
        for (byte[] response : knownResponses.values()) {
            if (Arrays.equals(response, bytes))
                return true;
        }
        return false;
    }

    public static String getResponseName(byte[] bytes) {
        for (Map.Entry<String, byte[]> entry : knownResponses.entrySet()) {
            if (Arrays.equals(entry.getValue(), bytes)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static String getCommandValue(String msg) {
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            if (entry.getKey().equals(msg)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static String getCommandName(String msg) {
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            if (entry.getValue().equals(msg)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean isCommand(String msg) {
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            if (entry.getValue().equals(msg)) {
                return true;
            }
        }
        return false;
    }
}
