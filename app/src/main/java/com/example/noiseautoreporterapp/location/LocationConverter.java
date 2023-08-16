package com.example.noiseautoreporterapp.location;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationConverter {

    public static final String LOCATION_CONVERSION_ERROR = "converter_error";
    public static String getAddress(Context context, Location location) {
        try {
            // Get a list of addresses for the location
            Locale defaultLocale = new Locale.Builder().setLanguage("en").setScript("Latn").setRegion("RS").build();
            List<Address> addresses = new Geocoder(context, defaultLocale).getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1);

            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                String fullAddress = address.getAddressLine(0); // Get the full address
                // You can also extract other address components like city, country, etc.
                return fullAddress;
            } else {
                return "unknown_location";
            }
        } catch(IOException e) {
            return LOCATION_CONVERSION_ERROR;
        }
    }

}
