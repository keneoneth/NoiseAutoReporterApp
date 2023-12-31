package com.example.noiseautoreporterapp.sender;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.EditText;

import com.example.noiseautoreporterapp.NoiseRecord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.Future;

public class RemoteRecordSender {

    private EditText mEtDeviceID = null;
    private EditText mEtAPIKey = null;

    public RemoteRecordSender(EditText deviceID, EditText remoteAPIKey) {
        this.mEtDeviceID = deviceID;
        this.mEtAPIKey = remoteAPIKey;
    }

    public boolean sendRecord(NoiseRecord noiseRecord) {

        // NOTE: comment the line below to use WebCommandClient
//        Future<String> authTokenFuture = UbidotsClient.requestAuthTokenAsync(this.mEtAPIKey.getText().toString());
        Future<String> authTokenFuture = WebCommandClient.requestAuthTokenAsync(this.mEtAPIKey.getText().toString());

        // You can perform other tasks here while waiting for the async request to complete
        try {
            // Wait for the async request to complete and get the result
            String authTokenResponse = authTokenFuture.get();

            if (authTokenResponse != null) {
                // Auth token request was successful
                Log.i("remote sender","Received auth token: " + authTokenResponse);
                JsonObject jsonObject = JsonParser.parseString(authTokenResponse).getAsJsonObject();
                String authToken = jsonObject.get("token").getAsString();
                // NOTE: comment the line below to use WebCommandClient
//                Future<String> sendDataFuture = UbidotsClient.sendDataAsync(authToken, noiseRecord);
                Future<String> sendDataFuture = WebCommandClient.sendDataAsync(this.mEtDeviceID.getText().toString(), authToken, noiseRecord);
                try {
                    // Wait for the async request to complete and get the result
                    String response = sendDataFuture.get();

                    if (response != null) {
                        // Data send request was successful
                        Log.i("remote sender", "Data sent successfully. Response: " + response);
                        return true;
                    } else {
                        // Data send request failed
                        Log.e("remote sender","Failed to send data.");
                        return false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }

            } else {
                // Auth token request failed
                Log.e("remote sender", "Failed to obtain auth token.");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }
}
