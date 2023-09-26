package com.example.noiseautoreporterapp;

import static android.content.ContentValues.TAG;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.noiseautoreporterapp.location.GPSReceiver;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_SETTINGS_REQUEST_CODE = 1;
    private static final int NOISE_RECORDER_PERMISSION_REQUEST_CODE = 100;
    private static final int GPS_RECEIVER_PERMISSION_REQUEST_CODE = 101;
    private static final int DEFAULT_NOISE_LEVEL = 80;
    private static final int DEFAULT_NOISE_FAST_FW_LEVEL = 10;
    private static final int DEFAULT_NOISE_FW_LEVEL = 1;
    private static final int NOISE_SCAN_FREQ = 100;
    private NoiseMeter noiseMeter = null;
    private GPSReceiver gpsReceiver = null;
    private NoiseRecorder noiseRecorder = null;
    private NoiseThresholdController noiseThresholdController = null;
    private ActivityResultLauncher<Intent> locationSettingsLauncher = null;
    private ArrayAdapter<String> noiseRecordAdapter = null;
    private ArrayList<String> displayNoiseRecordList = null;


    void setMainText(double curNoiseLevel, int curNoiseThreshold, String curLocation, String recordStatus) {
        TextView mainText = (TextView) findViewById(R.id.MAINTEXT);
        String mssg = "Noise Level: " + curNoiseLevel + " (dB)" +
                "\nNoise Threshold: " + curNoiseThreshold + " (dB)" +
                "\nLocation: " + curLocation +
                "\nRecord Status: " + recordStatus;
        mainText.setText(mssg);
    }

    void showToastMssg(String mssg) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, mssg, duration);
        toast.show();
    }

    void requestPermission(NoiseMeter noiseMeter) {
        ActivityCompat.requestPermissions(this, noiseMeter.getPermissions(), NOISE_RECORDER_PERMISSION_REQUEST_CODE);
    }

    void requestPermission(GPSReceiver gpsReceiver) {
        ActivityCompat.requestPermissions(this, gpsReceiver.getPermissions(), GPS_RECEIVER_PERMISSION_REQUEST_CODE);
    }

    void requestEnableGPS() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Please enable location services to proceed.")
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    enableLocationSettings();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void enableLocationSettings() {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        locationSettingsLauncher.launch(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // set up edit text
        EditText etAPIKey = findViewById(R.id.APIKEY);
        // set up tools
        gpsReceiver = new GPSReceiver(this);
        noiseRecorder = new NoiseRecorder(gpsReceiver,etAPIKey);
        displayNoiseRecordList = new ArrayList<String>();
        noiseRecordAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayNoiseRecordList) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                text.setTextColor(Color.BLACK);
                text.setTextSize(24);
                return view;
            }

        };
        noiseThresholdController = new NoiseThresholdController(DEFAULT_NOISE_LEVEL);
        noiseMeter = new NoiseMeter(this, noiseThresholdController, noiseRecorder);
        locationSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == LOCATION_SETTINGS_REQUEST_CODE) {
                        if (gpsReceiver != null && gpsReceiver.isGPSEnabled()) {
                            if (gpsReceiver.startTracking()) {
                                Log.i(TAG, "GPS Receiver: started location tracking");
                                showToastMssg("Start Location Tracking");
                                // start noise listening when location tracking is OK
                                startSoundMeter();
                            } else {
                                showToastMssg("Location Tracking Fail: unknown reason");
                            }
                        }
                    } else {
                        showToastMssg("Location Tracking Fail: GPS service not enabled");
                    }
                }
        );


        // set up buttons to control noise threshold levels
        ImageButton ibtnLeftFastForward = (ImageButton) findViewById(R.id.BUTTONLFFW);
        ibtnLeftFastForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                noiseThresholdController.decreaseNoiseLevel(DEFAULT_NOISE_FAST_FW_LEVEL);
            }
        });
        ImageButton ibtnLeftForward = (ImageButton) findViewById(R.id.BUTTONLFW);
        ibtnLeftForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                noiseThresholdController.decreaseNoiseLevel(DEFAULT_NOISE_FW_LEVEL);
            }
        });
        ImageButton ibtnRightForward = (ImageButton) findViewById(R.id.BUTTONRFW);
        ibtnRightForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                noiseThresholdController.increaseNoiseLevel(DEFAULT_NOISE_FW_LEVEL);
            }
        });
        ImageButton ibtnRightFastForward = (ImageButton) findViewById(R.id.BUTTONRFFW);
        ibtnRightFastForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                noiseThresholdController.increaseNoiseLevel(DEFAULT_NOISE_FAST_FW_LEVEL);
            }
        });

        // add noise display handler to show the current level of noise
        Handler noiseDisplayHandler = new Handler();
        noiseDisplayHandler.post(new Runnable() {
            @Override
            public void run() {
                setMainText(noiseMeter.getCurNoiseLevel(), noiseThresholdController.getNoiseThreshold(), gpsReceiver.getLocationAddress(), noiseRecorder.getRecordStatus());
                noiseDisplayHandler.postDelayed(this, NOISE_SCAN_FREQ); // set time here to refresh textView
            }
        });
        // connect noise record listview with adapter
        ListView noiseRecordLV = (ListView) findViewById(R.id.LISTVIEWNOISERECORD);
        noiseRecordLV.setAdapter(noiseRecordAdapter);
        Handler noiseRecordHandler = new Handler();
        noiseRecordHandler.post(new Runnable() {
            @Override
            public void run() {
                final int maxDisplaySize = 100;
                displayNoiseRecordList.clear();
                for (int i = Math.max(0,noiseRecorder.getRecordLength() - maxDisplaySize); i < noiseRecorder.getRecordLength(); i++) {
                    displayNoiseRecordList.add(0, noiseRecorder.getNoiseRecordListByIndex(i));
                }
                noiseRecordAdapter.notifyDataSetChanged();
                noiseRecordHandler.postDelayed(this,NOISE_SCAN_FREQ);
            }
        });

    }

    protected void startSoundMeter() {
        if (!noiseMeter.isListening()) {
            if (!noiseMeter.hasPermissions()) {
                requestPermission(noiseMeter);
            } else if (noiseMeter.startListening()) {
                Log.i(TAG, "Sound Meter: started listening");
                showToastMssg("Start Listening");
            } else {
                showToastMssg("Listening Fail: unknown reason");
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // start location tracking and noise monitoring tools
        if (!gpsReceiver.isTracking()) {
            // first, check if permission is granted
            if (!gpsReceiver.hasPermissions()) {
                requestPermission(gpsReceiver); // request for permissions
            } else {
                // second, check if gps is enabled
                if (!gpsReceiver.isGPSEnabled()) {
                    requestEnableGPS();
                } else if (gpsReceiver.startTracking()) {
                    Log.i(TAG, "GPS Receiver: started location tracking");
                    showToastMssg("Start Location Tracking");
                    // start noise listening when location tracking is OK
                    startSoundMeter();
                } else {
                    showToastMssg("Location Tracking Fail: unknown reason");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (noiseMeter != null && noiseMeter.stopListening()) {
            Log.i(TAG, "Sound Meter: stopped listening");
            showToastMssg("Stop Listening");
        }
        if (gpsReceiver != null && gpsReceiver.stopTracking()) {
            Log.i(TAG, "GPS Receiver: stopped tracking");
            showToastMssg("Stop Location Tracking");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allPermissionsGranted = true;

        for (int grantResult : grantResults) {
            System.out.println("grantResult : " + grantResult + " =? " + PackageManager.PERMISSION_GRANTED);
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break; // No need to check further if any permission is denied
            }
        }

        if (requestCode == NOISE_RECORDER_PERMISSION_REQUEST_CODE) {
            System.out.println("all permissions granted : " + allPermissionsGranted);
            if (allPermissionsGranted) {
                if (noiseMeter.startListening()) {
                    Log.i(TAG, "Sound Meter: started listening");
                    showToastMssg("Start Listening");
                } else {
                    showToastMssg("Listening Fail: unknown reason");
                }
            } else {
                showToastMssg("Listening Fail: permission not granted");
            }
        }

        if (requestCode == GPS_RECEIVER_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted) {
                if (gpsReceiver.isGPSEnabled()) {
                    if (gpsReceiver.startTracking()) {
                        Log.i(TAG, "GPS Receiver: started location tracking");
                        showToastMssg("Start Location Tracking");
                        // start noise listening when location tracking is OK
                        startSoundMeter();
                    } else {
                        showToastMssg("Location Tracking Fail: unknown reason");
                    }
                } else {
                    requestEnableGPS();
                }
            } else {
                showToastMssg("Location Tracking Fail: permission not granted");
            }
        }
    }
}