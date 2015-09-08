package org.thunderatz.tiago.thundertrekking.sensor;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorEventListener;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.LocationListener;
import android.location.LocationManager;
import android.provider.Settings;

import org.thunderatz.tiago.thundertrekking.Logger;

public class GPS extends SensorSocket {
    public GPS(Logger logger, int target_port, String my_id, SensorEventListener sensorEventListener) {
        super(logger, target_port, my_id);
        Context context = (Context) sensorEventListener;

        LocationManager locationManager;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            context.startActivity(intent);
        }
        locationManager.addNmeaListener((GpsStatus.NmeaListener) context);

        final String provider;
        Criteria criteria = new Criteria();
        // Ver http://developer.android.com/reference/android/location/Criteria.html
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        provider = locationManager.getBestProvider(criteria, false);
        if (provider != null) {
            log("provider " + provider + "\n");
            locationManager.requestLocationUpdates(provider, 0, 0, (LocationListener) sensorEventListener);
        } else
            log("sem gps\n");
    }

    public void send(String nmea) {
        send((nmea + "\r\n").getBytes());
    }
}