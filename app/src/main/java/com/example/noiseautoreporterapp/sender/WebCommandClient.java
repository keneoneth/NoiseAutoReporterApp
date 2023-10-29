package com.example.noiseautoreporterapp.sender;

import android.util.Log;

import com.example.noiseautoreporterapp.NoiseRecord;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WebCommandClient {

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
            String response = String.format("{\"token\": %s}", apiKey);
            return  response;
        });
    }

    public static Future<String> sendDataAsync(final String deviceID, final String authToken, NoiseRecord noiseRecord) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        return executor.submit(() -> {
            try {
                // Define the URL, API key, and data

                String apiUrl = "https://noisemeter.webcomand.com/ws/put";
                String jsonData = String.format("{ \"parent\": \"/Bases/nm1\","+
                            "\"data\": {" +
                            "\"type\": \"comand\"," +
                            "\"version\": \"1.0\"," +
                            "\"contents\":" +
                        "[" +
                            "{" +
                                "\"Type\": \"Noise\"," +
                                "\"Min\": %d," +
                                "\"Max\": %d," +
                                "\"DeviceID\": \"%s\"" +
                                "\"Location\": \"%s\"" +

                            "}" +
                        "]" +
                "}}",(int) noiseRecord.getMinNoiseLevel(), (int) noiseRecord.getNoiseLevel(), deviceID, noiseRecord.getLocation());


                // Create a URL object
                URL url = new URL(apiUrl);

                // Open a connection to the URL
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Set the request method to POST
                connection.setRequestMethod("POST");

                // Set request headers
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Token "+authToken);

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
                    Log.e("remote sender webcommand","send data "+jsonData+" with auth token "+authToken+" failed with code " + responseCode);
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
