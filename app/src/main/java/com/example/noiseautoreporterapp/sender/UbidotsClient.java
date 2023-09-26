package com.example.noiseautoreporterapp.sender;

import android.util.Log;

import com.example.noiseautoreporterapp.NoiseRecord;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class UbidotsClient {

    private static String convertStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static Future<String> requestAuthTokenAsync(final String apiKey) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        return executor.submit(() -> {
            try {
                // Define the URL and API key
                String apiUrl = "https://industrial.api.ubidots.com/api/v1.6/auth/token";

                // Create a URL object
                URL url = new URL(apiUrl);

                // Open a connection to the URL
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Set the request method to POST
                connection.setRequestMethod("POST");

                // Set request headers
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("x-ubidots-apikey", apiKey);

                // Enable input/output streams for the connection
                connection.setDoOutput(true);

                // Create an empty JSON object as the request data
                String requestData = "{}";

                // Write the request data to the output stream
                OutputStream os = connection.getOutputStream();
                byte[] input = requestData.getBytes("utf-8");
                os.write(input, 0, input.length);

                // Get the response code
                int responseCode = connection.getResponseCode();

                // Check if the request was successful (usually HTTP 200)
                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    // Read and process the response
                    InputStream responseStream = connection.getInputStream();
                    String response = convertStreamToString(responseStream);
                    return response;
                } else {
                    Log.e("remote sender ubidots","auth token failed with code " + responseCode);
                }

                // Close the connection
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        });
    }

    public static Future<String> sendDataAsync(final String authToken, NoiseRecord noiseRecord) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        return executor.submit(() -> {
            try {
                // Define the URL, API key, and data
                String apiUrl = "https://industrial.api.ubidots.com/api/v1.6/devices/noisemeter/";
                String jsonData = String.format("{ \"dBa\": %f, \"max\": %d, \"min\": %d }",
                        noiseRecord.getNoiseLevel(), 0, 0);

                // Create a URL object
                URL url = new URL(apiUrl);

                // Open a connection to the URL
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Set the request method to POST
                connection.setRequestMethod("POST");

                // Set request headers
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Auth-Token", authToken);

                // Enable input/output streams for the connection
                connection.setDoOutput(true);

                // Write the request data to the output stream
                OutputStream os = connection.getOutputStream();
                byte[] input = jsonData.getBytes("utf-8");
                os.write(input, 0, input.length);

                // Get the response code
                int responseCode = connection.getResponseCode();

                // Check if the request was successful (usually HTTP 200)
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read and process the response
                    InputStream responseStream = connection.getInputStream();
                    String response = convertStreamToString(responseStream);
                    return response;
                } else {
                    Log.e("remote sender ubidots","send data "+jsonData+" with auth token "+authToken+" failed with code " + responseCode);
                }

                // Close the connection
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        });
    }
}
