package de.kai_morich.simple_usb_terminal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BGapi {

    private static final HashMap<String, byte[]> knownResponses = new HashMap<>();

    static {
        knownResponses.put("scanner_set_mode", new byte[] {0x20, 0x02, 0x05, 0x02, 0x00, 0x00});
        knownResponses.put("scanner_set_timing", new byte[]{0x20, 0x02, 0x05, 0x01, 0x00, 0x00});
        knownResponses.put("connection_set_default_parameters", new byte[] {0x20, 0x02, 0x06, 0x00, 0x00, 0x00});
        knownResponses.put("scanner_start", new byte[] {0x20, 0x02, 0x05, 0x03, 0x00, 0x00});
        knownResponses.put("scanner_stop", new byte[] {0x20, 0x02, 0x05, 0x05, 0x00, 0x00});
    }

    public static final byte[] SET_MODE_RESPONSE = {0x20, 0x02, 0x05, 0x02, 0x00, 0x00};
    public static final byte[] SET_TIMING_RESPONSE = {0x20, 0x02, 0x05, 0x01, 0x00, 0x00};
    public static final byte[] SET_PARAM_RESPONSE = {0x20, 0x02, 0x06, 0x00, 0x00, 0x00};

    public static final String START_CMD      = "200205030402";
    public static final byte[] START_RESPONSE = {0x20, 0x02, 0x05, 0x03, 0x00, 0x00};
    public static final String STOP_CMD       = "20000505";
    public static final byte[] STOP_RESPONSE  = {0x20, 0x02, 0x05, 0x05, 0x00, 0x00};
    public static final String ROTATE_CW_CMD = "2002FF000101";
    public static final String ROTATE_CCW_CMD = "2002FF000102";
    public static final String ROTATE_STOP_CMD = "2002FF000103";

    public static boolean isScanReportEvent(byte[] bytes){
        return bytes[0] == (byte)0xA1 && bytes[1] == 0x00
                && bytes[2] == 0x05 && bytes[3] == 0x01;
    }

    public static boolean isStartResponse(byte[] bytes){
        return Arrays.equals(bytes, START_RESPONSE);
    }

    public static boolean isStopResponse(byte[] bytes){
        return Arrays.equals(bytes, STOP_RESPONSE);
    }

    public static boolean isKnownResponse(byte[] bytes){
        for(byte[] response : knownResponses.values()){
            if(Arrays.equals(response, bytes))
                return true;
        }
        return false;
    }

    public static String getResponseName(byte[] bytes){
        for(Map.Entry<String, byte[]> entry : knownResponses.entrySet()){
            if(Arrays.equals(entry.getValue(), bytes)){
                return entry.getKey();
            }
        }
        return null;
    }
}
