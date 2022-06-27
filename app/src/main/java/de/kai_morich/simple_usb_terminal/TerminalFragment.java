package de.kai_morich.simple_usb_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.SerialTimeoutException;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

@RequiresApi(api = Build.VERSION_CODES.O)
public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected {False, Pending, True}

    private final BroadcastReceiver broadcastReceiver;
    private int deviceId, portNum, baudRate;
    private UsbSerialPort usbSerialPort;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private ControlLines controlLines;
    private TextUtil.HexWatcher hexWatcher;
    private BlePacket pendingPacket;
    private FileWriter fw;
    private File file;
    private Timer uploadTimer, motorTimer, testTimer;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = true;
    private boolean controlLinesEnabled = false;
    private boolean pendingNewline = false;
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

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        uploadTimer.cancel();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startForegroundService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
//        if (service != null && !getActivity().isChangingConfigurations())
//            service.detach();

        status("onStop");

        try {
            fw.write("onStop\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        //TODO: broke
//        status("onAttach");
//        getActivity().startForegroundService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }

        status("onDetach");
        try {
            fw.write("onDetach\n");
        } catch (IOException e) {
            e.printStackTrace();
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
        if (controlLinesEnabled && controlLines != null && connected == Connected.True)
            controlLines.start();
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(broadcastReceiver);
        if (controlLines != null)
            controlLines.stop();

        status("onPause");
        try {
            fw.write("onPause\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

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

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        View setupBtn = view.findViewById(R.id.setup_btn);
        setupBtn.setOnClickListener(view1 -> {
            send(BGapi.SCANNER_SET_MODE);
            send(BGapi.SCANNER_SET_TIMING);
            send(BGapi.CONNECTION_SET_PARAMETERS);
        });

        View startBtn = view.findViewById(R.id.start_btn);
        startBtn.setOnClickListener(v -> send(BGapi.SCANNER_START));

        View stopBtn = view.findViewById(R.id.stop_btn);
        stopBtn.setOnClickListener(v -> send(BGapi.SCANNER_STOP));

        View saveBtn = view.findViewById(R.id.save_btn);
//        saveBtn.setOnClickListener(v -> onSave());

        File path = getContext().getExternalFilesDir(null);
        file = new File(path, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss")) + "_log.txt");
        try {
            fw = new FileWriter(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        receiveText.append("Writing to " + file.getAbsolutePath() + "\n");

        uploadTimer = new Timer();
        startTimer();

        motorTimer = new Timer();

        testTimer = new Timer();
//        testTimer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                status("testUpload");
//                Activity act = getActivity();
//                if (act instanceof MainActivity) {
//                    ((MainActivity) act).testUpload("FragmentTimer");
//                }
//            }
//        }, 0, 60000 /*1 minute*/);

        controlLines = new ControlLines(view);
        return view;
    }


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
            uploadLog();
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
        } else if (id == R.id.autoRotate) {
            motorTimer = new Timer();
            motorTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    send(BGapi.ROTATE_CW);
                    SystemClock.sleep(rotatePeriod);
                    send(BGapi.ROTATE_STOP);
                }
            }, 0, rotatePeriod * 2L);
            return true;
        } else if (id == R.id.stopAutoRotate) {
            motorTimer.cancel();
            return true;
        } else if (id == R.id.editRotate) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("New Rotation Period");

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

    /*
     * Serial + UI
     */
    private void connect() {
        connect(null);
    }

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
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), 0);
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
        startTimer();
    }

    private void disconnect() {
        connected = Connected.False;
        controlLines.stop();
        service.disconnect();
        stopTimer();

        usbSerialPort = null;
    }

    private void send(String str) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if (hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
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

    private void receive(byte[] data) {
        if (hexEnabled) {
            if (BGapi.isScanReportEvent(data)) {
                //original script recorded time, addr, rssi, channel, and data
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
                    try {
                        fw.write(pendingPacket.toCSV());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                pendingPacket = BlePacket.parsePacket(data);
            } else if (BGapi.isKnownResponse(data)) {
                receiveText.append(BGapi.getResponseName(data) + '\n');
            } else {
                //until the data has a terminator, assume packets that aren't a known header are data that was truncated
                if (pendingPacket != null)
                    pendingPacket.appendData(data);
//                receiveText.append(TextUtil.toHexString(data) + '\n');
            }
        } else {
            String msg = new String(data);
            if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    private void uploadLog() {
        //clear the log
        receiveText.setText("");

        //close the fileWriter
        try {
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //upload the log
        Activity act = getActivity();
        if (act instanceof MainActivity) {
            ((MainActivity) act).uploadFile(file);
        }

        //create new file + fileWriter
        File path = getContext().getExternalFilesDir(null);
        file = new File(path, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss")) + "_log.txt");
        try {
            fw = new FileWriter(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        receiveText.append("Writing to " + file.getAbsolutePath() + "\n");
    }

    private void startTimer() {
        uploadTimer.schedule(new TimerTask() {
                                 @Override
                                 public void run() {
                                     uploadLog();
                                 }
                             }, 0,
                120000 /*2 minutes*/
//                900000 /*15 minutes*/
        );
    }

    private void stopTimer() {
        uploadTimer.cancel();
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        if (controlLinesEnabled)
            controlLines.start();
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

    class ControlLines {
        private static final int refreshInterval = 200; // ms

        private final Handler mainLooper;
        private final Runnable runnable;
        private final LinearLayout frame;
        private final ToggleButton rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn;

        ControlLines(View view) {
            mainLooper = new Handler(Looper.getMainLooper());
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

            frame = view.findViewById(R.id.controlLines);
            rtsBtn = view.findViewById(R.id.controlLineRts);
            ctsBtn = view.findViewById(R.id.controlLineCts);
            dtrBtn = view.findViewById(R.id.controlLineDtr);
            dsrBtn = view.findViewById(R.id.controlLineDsr);
            cdBtn = view.findViewById(R.id.controlLineCd);
            riBtn = view.findViewById(R.id.controlLineRi);
            rtsBtn.setOnClickListener(this::toggle);
            dtrBtn.setOnClickListener(this::toggle);
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (connected != Connected.True) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
            try {
                if (btn.equals(rtsBtn)) {
                    ctrl = "RTS";
                    usbSerialPort.setRTS(btn.isChecked());
                }
                if (btn.equals(dtrBtn)) {
                    ctrl = "DTR";
                    usbSerialPort.setDTR(btn.isChecked());
                }
            } catch (IOException e) {
                status("set" + ctrl + " failed: " + e.getMessage());
            }
        }

        private void run() {
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                rtsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
                ctsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
                dtrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
                dsrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
                cdBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
                riBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            frame.setVisibility(View.VISIBLE);
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS))
                    rtsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS))
                    ctsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR))
                    dtrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR))
                    dsrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))
                    cdBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))
                    riBtn.setVisibility(View.INVISIBLE);
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            frame.setVisibility(View.GONE);
            mainLooper.removeCallbacks(runnable);
            rtsBtn.setChecked(false);
            ctsBtn.setChecked(false);
            dtrBtn.setChecked(false);
            dsrBtn.setChecked(false);
            cdBtn.setChecked(false);
            riBtn.setChecked(false);
        }
    }

}
