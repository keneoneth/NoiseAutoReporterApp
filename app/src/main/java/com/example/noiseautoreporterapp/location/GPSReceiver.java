package com.example.noiseautoreporterapp.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

public class GPSReceiver extends Service implements LocationListener {

    public static final String GPS_RECEIVER_ERROR = "gps_receiver_error";
    private static final String[] RECEIVER_PERMISSION = {
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    private static final int LOCATION_UPDATE_FREQ = 1000;
    private static final int LOCATION_UPDATE_DIST_M = 1;
    private boolean mIsTracking = false;
    private Context mContext = null;
    private LocationManager mLocationManager = null;
    Location curlocation = null;

    public boolean isTracking() {
        return mIsTracking;
    }

    public String[] getErrors() {
        return new String[]{GPS_RECEIVER_ERROR,LocationConverter.LOCATION_CONVERSION_ERROR};
    }
    public GPSReceiver(Context context) {
        this.mContext = context;
        this.mLocationManager = (LocationManager) this.mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    public String getLocationAddress() {
        Log.i("gps receiver", "getLocationAddress: permission:"+hasPermissions()+"|isTracking:"+mIsTracking+"|curLocation:"+curlocation);
        if (hasPermissions() && mIsTracking && curlocation != null)
            return LocationConverter.getAddress(mContext, curlocation);
        else
            return GPS_RECEIVER_ERROR;
    }

    public boolean hasPermissions() {
        for (String permission : RECEIVER_PERMISSION) {
            if (ActivityCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public boolean isGPSEnabled() {
        return this.mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @SuppressLint("MissingPermission")
    public boolean startTracking() {
        if (!hasPermissions())
            return false;
        if (!this.isGPSEnabled())
            return false;
        if (mIsTracking)
            return false;

        this.mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_FREQ,
                LOCATION_UPDATE_DIST_M,
                this);
        mIsTracking = true;
        return true;
    }

    public boolean stopTracking() {
        if (this.mLocationManager != null && mIsTracking) {
            this.mLocationManager.removeUpdates(this);
            mIsTracking = false;
            return true;
        } else {
            return false;
        }
    }

    public String[] getPermissions() {
        return RECEIVER_PERMISSION;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        this.curlocation = location;
    }
}
