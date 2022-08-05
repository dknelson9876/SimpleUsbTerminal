package de.kai_morich.simple_usb_terminal;

public interface SerialReceiver {

    enum MessageType {
        SCAN_REPORT,
        TEMP_REPORT,
        GENERIC_RESPONSE,
        CONNECT,
        CONNECT_ERROR,
        IO_ERROR
    }

    void onReceiveScanReport(BlePacket packet);
    void onReceiveTempReport(int temp);
    void onReceiveGenericResponse(String response);

    void onSerialConnect();
    void onSerialConnectError(Exception e);
    void onSerialIoError(Exception e);

}
