package com.example.noiseautoreporterapp;

import java.text.SimpleDateFormat;
import java.util.Date;

public class NoiseRecord {

    private Date mRecordDate; // date of the record happened
    private String mLocation; // location of the record happened
    private double mNoiseLevel; // noise level of record
    private double mMinNoiseLevel;
    private boolean mRemoteSendStatus;
    public NoiseRecord (Date recordDate, String location, double noiseLevel) {
        this.mRecordDate = recordDate;
        this.mLocation = location;
        this.mNoiseLevel = noiseLevel;
        this.mMinNoiseLevel = noiseLevel;
        this.mRemoteSendStatus = false;
    }
    public NoiseRecord (Date recordDate, String location, double maxNoiseLevel, double minNoiseLevel) {
        this.mRecordDate = recordDate;
        this.mLocation = location;
        this.mNoiseLevel = maxNoiseLevel;
        this.mMinNoiseLevel = minNoiseLevel;
        this.mRemoteSendStatus = false;
    }
    private String getTimeStamp(Date date) {
        // Define a date-time formatter
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        // Format the date-time using the formatter
        return formatter.format(date);
    }
    public String getTimeStamp() {
        return getTimeStamp(this.mRecordDate);
    }
    public String getLocation() {
        return mLocation;
    }
    public double getNoiseLevel() {
        return mNoiseLevel;
    }
    public double getMinNoiseLevel() {
        return mMinNoiseLevel;
    }
    public void setRemoteSendStatus(boolean remoteSendStatus) {
        this.mRemoteSendStatus = remoteSendStatus;
    }
    public String toString() {
        return this.getTimeStamp()+" - ("+this.mLocation+") "+this.mNoiseLevel+" dB - (api send:"+(this.mRemoteSendStatus ? "OK":"FAIL")+")";
    }
}
