package de.kai_morich.simple_usb_terminal;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * A class that holds all the details we want out of a BT packet received by the Gecko
 *
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

    /**
     * Constructor that grabs all necessary details about the current state of the device
     *  (datetime, heading, location), and stores them with the details of the packet
     * */
    @SuppressLint("NewApi")
    private BlePacket(String addr, byte rssi, byte channel, byte packet_type, byte[] data) {
        time = LocalDateTime.now();
        heading = SensorHelper.getHeading();

        Location location = LocationBroadcastReceiver.Companion.getCurrentLocation();
        if (location != null) {
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
    }

    /**
     * Given a byte[] that is long enough, parses and returns the new organized BlePacket
     *  object representing the data that was received and the state of the device
     *
     * @return the newly created packet, or null if bytes was too short*/
    public static BlePacket parsePacket(byte[] bytes) {
        //scanner_extended_advertisement_report ->
        //  A100 0502 bgapi identifier
        //  00      event_flags
        //  B1 873A 6934 94     advertiser address
        //  00      addr type
        //  FF      bonding
        //  E1      rssi
        //  1E      channel
        //  00 0000 0000 00
        //  00      target addr type
        //  00      advertising set identifier
        //  04      primary phy
        //  04      secondary phy
        //  7F      tx power
        //  0000    periodic interval
        //  00      data completeness
        //  29      counter
        //  E5      ????
        //  ...DATA
        // event flags: 0
        // address: 94:34:69:3A:87:81
        //  address type: 0
        // bonding: 255
        // rssi: -31
        // channel: 31
        //  target addr: 0000 0000 0000
        //  target_addr_type: 0
        // adv_sid: 0
        // pri phy: 4
        // sec phy: 4
        // tx_power: 127
        // periodic interval: 0
        // data completeness: 0
        // counter: 40
        // Data: .....
        if (bytes.length < 32)
            return null;
        String addr = "";
        for(int i = 10; i > 5; i--){
            addr += String.format("%02X", bytes[i]) + ":";
        }
        addr = addr.substring(0, addr.length() - 1);
        byte packet_type = 0;
        byte rssi = bytes[13];
        byte channel = bytes[14];

        byte[] data = Arrays.copyOfRange(bytes, 32, bytes.length);
        return new BlePacket(addr, rssi, channel, packet_type, data);
    }

    /**
     * Adds the contents of bytes to the end of the data of an already existing packet
     * */
    public void appendData(byte[] bytes) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(data);
            stream.write(bytes);
            data = stream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Returns the contents of this packet in a human readable form
     * */
    @NonNull
    @SuppressLint("NewApi")
    @Override
    public String toString() {
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                + "\nLat: " + latt
                + "\nLong: " + longg
                + "\nHead: " + heading
                + "\nAddr: " + addr
                + "\nRSSI: " + rssi
                + "\nChannel: " + (channel & 0xFF /*'cast' to unsigned*/)
                + "\nPacket Type: 0x" + String.format("%02X", packet_type)
                + "\nData: " + TextUtil.toHexString(data);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String toShortString(){
        return "Datetime: "+time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                +"\nAddr: "+addr
                +"\nData: "+TextUtil.toHexString(data)
                ;
    }

    /**
     * Returns the contents of this packet formatted as a single line for a csv file
     * */
    @SuppressLint("NewApi")
    public String toCSV() {
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"))
                + "," + latt
                + "," + longg
                + "," + heading
                + "," + addr
                + "," + rssi
                + "," + (channel & 0xFF)
                + "," + TextUtil.toHexString(data) + "\n";
    }



}
