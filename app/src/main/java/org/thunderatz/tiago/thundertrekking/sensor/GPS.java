package org.thunderatz.tiago.thundertrekking.sensor;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.LocationListener;
import android.location.LocationManager;
import android.provider.Settings;

import org.thunderatz.tiago.thundertrekking.Logger;

public class GPS extends SensorThread {
    SensorManager mSensorManager;
    SensorEventListener activity;
    public GPS(Logger l, int target_port, String my_id, Context context) {
        super(l, target_port, my_id);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        activity = (SensorEventListener) context;

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
            log("gps: provider " + provider + "\n");
            locationManager.requestLocationUpdates(provider, 0, 0, (LocationListener) activity);
        } else
            log("gps: sem gps\n");
    }

    @Override
    public boolean register() {return true;}

    @Override
    public void unregister() {}

    public void send(String nmea) {
        send((nmea + "\r\n").getBytes());
    }
}