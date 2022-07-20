package de.kai_morich.simple_usb_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.Priority;
import com.hoho.android.usbserial.driver.SerialTimeoutException;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The UI portion of the app that is displayed while connected to a USB serial device
 * There's a lot of non-UI logic still in here that needs to be cleaned up and/or removed
 * entirely as we won't actually use it
 * */
@RequiresApi(api = Build.VERSION_CODES.O)
public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private void onSetupClicked(View view1) {
        send(BGapi.SCANNER_SET_MODE);
        send(BGapi.SCANNER_SET_TIMING);
        send(BGapi.CONNECTION_SET_PARAMETERS);
    }

    private void onStartClicked(View v) {
        send(BGapi.SCANNER_START);
    }

    private enum Connected {False, Pending, True}

    private final BroadcastReceiver broadcastReceiver;
    private int deviceId, portNum, baudRate;
    private UsbSerialPort usbSerialPort;
    private SerialService service;

    private TextView receiveText;
    private BlePacket pendingPacket;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean truncate = true;
    private String newline = TextUtil.newline_crlf;
    private int rotatePeriod = 500;

    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Constants.INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
    }

    //region Lifecycle

    /**
     * Inherited from Fragment. One of the first methods that the system will call
     * after the constructor. Retrieves the information about the device to connect to
     * that was sent over by the DevicesFragment
     * */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
    }

    /**
     * Inherited from Fragment. Called by the system when the app gets closed
     * */
    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    /**
     * Inherited from Fragment. Called by the system
     * */
    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    /**
     * Inherited from Fragment. Called by the system. Unsubscribes from messages from the serial device
     * as this Fragment is no longer being displayed
     * */
    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB));
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }



    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        View setupBtn = view.findViewById(R.id.setup_btn);
        setupBtn.setOnClickListener(this::onSetupClicked);

        View stopUploadBtn = view.findViewById(R.id.stop_upload_btn);
        stopUploadBtn.setOnClickListener(btn -> {
            Toast.makeText(getContext(), "click!", Toast.LENGTH_SHORT).show();
            Intent stopIntent = new Intent(getContext(), FirebaseService.ActionListener.class);
            stopIntent.setAction(FirebaseService.KEY_NOTIFICATION_STOP_ACTION);
            stopIntent.putExtra(FirebaseService.KEY_NOTIFICATION_ID, ServiceNotification.notificationId);
            FirebaseService.Companion.getInstance().sendBroadcast(stopIntent);
        });

        SwitchCompat stopMotorBtn = view.findViewById(R.id.stop_motor_btn);
        stopMotorBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Intent stopMotorIntent = new Intent(getContext(), SerialService.ActionListener.class);
                stopMotorIntent.setAction(SerialService.KEY_STOP_MOTOR_ACTION);
                stopMotorIntent.putExtra(SerialService.KEY_MOTOR_SWITCH_STATE, isChecked);
                SerialService.getInstance().sendBroadcast(stopMotorIntent);
            }
        });

        View startBtn = view.findViewById(R.id.start_btn);
        startBtn.setOnClickListener(this::onStartClicked);

        View stopBtn = view.findViewById(R.id.stop_btn);
        stopBtn.setOnClickListener(v -> send(BGapi.SCANNER_STOP));

        Spinner gps_priority = view.findViewById(R.id.gps_priority_spinner);
        String[] gps_options = {"Power Saving", "Balanced", "High Accuracy"};
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(), android.R.layout.simple_spinner_item, gps_options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gps_priority.setAdapter(adapter);
        gps_priority.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Activity activity = getActivity();
                if(activity instanceof MainActivity) {
                    switch(position){
                        case 0:
                            ((MainActivity) activity).updateLocationPriority(Priority.PRIORITY_LOW_POWER);
                            break;
                        case 1:
                            ((MainActivity) activity).updateLocationPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY);
                            break;
                        case 2:
                            ((MainActivity) activity).updateLocationPriority(Priority.PRIORITY_HIGH_ACCURACY);
                            break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //do nothing
            }
        });

        //TODO switch to get the filename directly from FirebaseService
        receiveText.append("Writing to " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")) + "_log.txt" + "\n");

        return view;
    }

    /**
     * Inherited from Fragment. The Options menu is the 3 dots in the top right corner
     * */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.truncate).setChecked(truncate);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.manualUpload) {
            FirebaseService.Companion.getInstance().uploadLog();
            return true;
        } else if (id == R.id.truncate) {
            truncate = !truncate;
            item.setChecked(truncate);
            return true;
        } else if (id == R.id.manualCW) {
            send(BGapi.ROTATE_CW);
            SystemClock.sleep(500);
            send(BGapi.ROTATE_STOP);
            return true;
        } else if (id == R.id.manualCCW) {
            send(BGapi.ROTATE_CCW);
            SystemClock.sleep(500);
            send(BGapi.ROTATE_STOP);
            return true;
        } else if (id == R.id.editRotate) {
            //TODO actually change the period in SerialService
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("New Rotation Period UNUSED");

            final EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                rotatePeriod = Integer.parseInt(input.getText().toString());
                Toast.makeText(getContext(), "Set rotation period to " + rotatePeriod, Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
            builder.show();

            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    // endregion Lifecycle

    //region Serial


    private void connect() {
        connect(null);
    }

    /**
     * Do all the listing and check required to connect to the USB device using the details
     * that were passed when this Fragment was started
     * But some of this seems like duplicate logic from DevicesFragment, so this might be able
     * to be reduced
     * */
    private void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getDeviceId() == deviceId)
                device = v;
        if (device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if (driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        //TODO: Non-UI logic - should not be in a UI class
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        usbSerialPort = null;
    }

    /**
     * Send a String to the currently connected serial device. Returns immediately if no
     * device is connected. Additionally appends the sent information to the text on screen
     * */
    private void send(String str) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            //TODO: Non-UI logic - should not be in UI class
            String msg;
            byte[] data;
            StringBuilder sb = new StringBuilder();
            TextUtil.toHexString(sb, TextUtil.fromHexString(str));
            TextUtil.toHexString(sb, newline.getBytes());
            msg = sb.toString();
            data = TextUtil.fromHexString(msg);
            if (BGapi.isCommand(str)) {
                msg = BGapi.getCommandName(str);
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (SerialTimeoutException e) {
            status("write timeout: " + e.getMessage());
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    /**
     * Parse the bytes that were received from the serial device. If those bytes are recognized
     * as a message that is part of BGAPI, prints the message name rather than the bytes
     * If the message is a packet, parse it into a packet object
     * */
    private void receive(byte[] data) {
        if (BGapi.isScanReportEvent(data)) {
            //original script recorded time, addr, rssi, channel, and data
            //TODO: Non-UI logic - should not be in UI class
            if (pendingPacket != null) {
                String msg = pendingPacket.toString();
                if (truncate) {
                    int length = msg.length();
                    if (length > msg.lastIndexOf('\n') + 40) {
                        length = msg.lastIndexOf('\n') + 40;
                    }
                    msg = msg.substring(0, length) + "…";
                }
                SpannableStringBuilder spn = new SpannableStringBuilder(msg + "\n\n");
                spn.setSpan(new ForegroundColorSpan(Color.MAGENTA), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                receiveText.append(spn);
            }
            if (data.length <= 21)
                return;

            pendingPacket = BlePacket.parsePacket(data);
        } else if (BGapi.isKnownResponse(data)) {
            String rsp = BGapi.getResponseName(data);
            if(rsp != null && !rsp.contains("rotate"))
                receiveText.append(BGapi.getResponseName(data) + '\n');
        } else {
            //until the data has a terminator, assume packets that aren't a known header are data that was truncated
            if (pendingPacket != null)
                pendingPacket.appendData(data);
        }

        //If the text in receiveText is getting too large to be reasonable, cut it off
        if(receiveText.getText().length() > 8000){
            CharSequence text = receiveText.getText();
            int length = text.length();
            receiveText.setText(text.subSequence(length-2000, length));
        }

    }

    /**
     * Print to the textview in a different color so that it stands out
     * */
    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    //endregion

    //region SerialListener


    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        //send setup and start commands after delay via custom Handler
        Handler handler = new Handler();
        Runnable clickSetup = () -> onSetupClicked(null);
        handler.postDelayed(clickSetup, 2500);
        Runnable clickStart = () -> onStartClicked(null);
        handler.postDelayed(clickStart, 2700);
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
