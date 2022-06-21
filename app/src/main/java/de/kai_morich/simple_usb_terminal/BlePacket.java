package de.kai_morich.simple_usb_terminal;

import android.annotation.SuppressLint;
import android.hardware.SensorManager;
import android.location.Location;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

/**
 * https://docs.silabs.com/bluetooth/3.2/group-sl-bt-evt-scanner-scan-report
 */

public class BlePacket {
    private LocalDateTime time;
    private double latt;
    private double longg;
    private double heading;
    private String addr;
    private byte rssi;
    private byte channel;
    private byte packet_type;
    private byte[] data;
    private double temperature;
    private byte[] temperature_bytes;

    @SuppressLint("NewApi")
    public BlePacket(String addr, byte rssi, byte channel, byte packet_type, byte[] data, float temperature, byte[] meas){
        time = LocalDateTime.now();
        heading = SensorHelper.getHeading();

        Location location = MainActivity.getLocation();
        if(location != null) {
            latt = location.getLatitude();
            longg = location.getLongitude();
        } else {
            latt = 0;
            longg = 0;
        }

        this.addr = addr;
        this.rssi = rssi;
        this.channel = channel;
        this.packet_type = packet_type;
        this.data = data;
        this.temperature = temperature;
        this.temperature_bytes = meas;
    }

    public static BlePacket parsePacket(byte[] bytes){
        String addr = "";
        for(int i = 10; i > 4; i--){
            addr += String.format("%02X", bytes[i]) + ":";
        }
        addr = addr.substring(0, addr.length()-1);
        byte packet_type = bytes[4];
        byte rssi = bytes[17];
        byte channel = bytes[18];
        byte[] data = Arrays.copyOfRange(bytes, 21, bytes.length);

        float temperature = 0.0f;
        byte[] meas = new byte[4];
        if (data.length > 42) {
            meas = Arrays.copyOfRange(data, 38, 42);
            temperature = ByteBuffer.wrap(meas).getFloat();
        }

        return new BlePacket(addr, rssi, channel, packet_type, data, temperature, meas);
    }



    public void appendData(byte[] bytes){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(data);
            stream.write(bytes);
            data = stream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    @Override
    public String toString(){
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                +"\nLat: "+latt
                +"\nLong: "+longg
                +"\nHead: "+heading
                +"\nAddr: "+addr
                +"\nRSSI: "+rssi
                +"\nChannel: "+(channel & 0xFF /*'cast' to unsigned*/)
                +"\nPacket Type: 0x"+String.format("%02X", packet_type)
                +"\nTemperature: "+temperature+" deg"
                +"\nTemper Bytes: "+TextUtil.toHexString(temperature_bytes)
                +"\nData: "+TextUtil.toHexString(data);
    }

    @SuppressLint("NewApi")
    public String toCSV(){
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                +","+latt
                +","+longg
                +","+heading
                +","+addr
                +","+rssi
                +","+(channel & 0xFF)
                +","+temperature
                +","+TextUtil.toHexString(temperature_bytes)
                +","+TextUtil.toHexString(data)+"\n";
    }



}
