package de.kai_morich.simple_usb_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {

    private static FusedLocationProviderClient fusedLocationClient;
    private static CurrentLocationRequest request = new CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(10).build();
    private static Location location;
    private Timer timer;
    private StorageReference storageRef;

    ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts
                            .RequestMultiplePermissions(), result -> {
                        Boolean fineLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_FINE_LOCATION, false);
                        Boolean coarseLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_COARSE_LOCATION, false);
                        if (fineLocationGranted != null && fineLocationGranted) {
                            // Precise location access granted.
                        } else if (coarseLocationGranted != null && coarseLocationGranted) {
                            // Only approximate location access granted.
                        } else {
                            // No location access granted.
                        }
                    }
            );

    @SuppressLint("MissingPermission")
    public static Location getLocation(){
        return location;
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        startService(new Intent(this, SensorHelper.class));

        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation().addOnSuccessListener(newLocation ->{
            location = newLocation;
        });
        timer = new Timer();
        timer.schedule(new TimerTask(){
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                fusedLocationClient.getCurrentLocation(request, new CancellationToken() {
                    @SuppressLint("MissingPermission")
                    @NonNull
                    @Override
                    public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                        return null;
                    }

                    @Override
                    public boolean isCancellationRequested() {
                        return false;
                    }
                }).addOnSuccessListener( newLocation ->{
                    location = newLocation;
                });
            }
        }, 0, 1000);


        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();

//        File path = getExternalFilesDir(null);
//        File file = new File(path, "thing.txt");
//        FileWriter fw;
//        try {
//            fw = new FileWriter(file);
//            fw.write("This is some text\nwith some more text\nand more\nand more");
//            fw.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        Uri uri = Uri.fromFile(file);
//        StorageReference fileRef = storageRef.child("log/thing.txt");
//        fileRef.putFile(uri);


        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
        else
            onBackStackChanged();
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            TerminalFragment terminal = (TerminalFragment)getSupportFragmentManager().findFragmentByTag("terminal");
            if (terminal != null)
                terminal.status("USB device detected");
        }
        super.onNewIntent(intent);
    }

    public void testUpload(){

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();

        File path = getExternalFilesDir(null);
        File file = new File(path, "MainActivity#testUpload.txt");
        FileWriter fw;
        try {
            fw = new FileWriter(file);
            fw.write("This is some text");
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Uri uri = Uri.fromFile(file);
        StorageReference fileRef = storageRef.child("log/MainActivity#testUpload.txt");
        fileRef.putFile(uri);
    }

    public void uploadFile(File file){
        Uri uri = Uri.fromFile(file);
        StorageReference fileRef = storageRef.child("log/"+uri.getLastPathSegment());
        fileRef.putFile(uri);
    }

}
