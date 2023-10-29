package com.example.noiseautoreporterapp;

import android.content.Context;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import com.example.noiseautoreporterapp.location.GPSReceiver;
import com.example.noiseautoreporterapp.sender.RemoteRecordSender;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.Date;

public class NoiseRecorder {

    public static final int MAX_STORAGE_SIZE = 100000;
    public static final String GPS_RECEIVER_NULL_ERROR = "gps_null";
    public static final String INVALID_RECORD_ERROR = "invalid_record";
    private ArrayList<String> noiseRecordList = null;
    private GPSReceiver mGPSReceiver = null;
    private RemoteRecordSender mRemoteRecordSender = null;
    public NoiseRecorder (GPSReceiver gpsReceiver, EditText etDevID, EditText etAPIKey) {
        this.noiseRecordList = new ArrayList<>();
        this.mGPSReceiver = gpsReceiver;
        this.mRemoteRecordSender = new RemoteRecordSender(etDevID, etAPIKey);
    }
    private boolean checkLocationValid(String location) {
        if (location.equals(GPS_RECEIVER_NULL_ERROR))
            return false;
        for (String error : this.mGPSReceiver.getErrors()) {
            if (location.equals(error))
                return false;
        }
        return true;
    }

    public String getRecordStatus() {
        return  (checkLocationValid(getLocation())) ? "OK" : "Waiting for address";
    }
    private String getLocation() {
        if (this.mGPSReceiver == null)
            return GPS_RECEIVER_NULL_ERROR;
        return this.mGPSReceiver.getLocationAddress();
    }

    public int getRecordLength() {
        return this.noiseRecordList.size();
    }

    public String addRecord(double minNoiseLevel, double maxNoiseLevel) {
        String curLocation = getLocation();
        if (checkLocationValid(curLocation)) {
            Date currentDate = new Date();
            NoiseRecord noiseRecord = new NoiseRecord(currentDate, curLocation, minNoiseLevel, maxNoiseLevel);
            Log.i("noise recorder", "add record " + noiseRecord.toString());
            // send to remote
            boolean sendStatus = mRemoteRecordSender.sendRecord(noiseRecord);
            noiseRecord.setRemoteSendStatus(sendStatus);
            // add to local list
            this.noiseRecordList.add(noiseRecord.toString()); // always add the latest item to the back
            if (this.noiseRecordList.size() > MAX_STORAGE_SIZE) {
                this.noiseRecordList.remove(0);
            }

            return noiseRecord.getTimeStamp();
        }
        return INVALID_RECORD_ERROR;
    }

    public String getNoiseRecordListByIndex(int index) {
        if (index < getRecordLength())
            return noiseRecordList.get(index);
        else
            return "null";
    }

}
