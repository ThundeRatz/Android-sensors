package org.thunderatz.tiago.thundertrekking;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import org.thunderatz.tiago.thundertrekking.sensor.GPS;
import org.thunderatz.tiago.thundertrekking.sensor.IMU;
import org.thunderatz.tiago.thundertrekking.sensor.Proximity;

import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener, LocationListener, GpsStatus.NmeaListener {
    private TextView log;
    private SensorManager mSensorManager;
    private GPS gps;
    private IMU imu;
    private Proximity proximity;

    Logger logger = new Logger() {
        @Override
        public void add(final String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log.append(msg);
                }
            });
        }
    };

    protected void listInterfaces() {
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();
                    log.append("if " + networkInterface.getName() + " (" + networkInterface.getDisplayName() + "): ");
                    if (inetAddress.isLoopbackAddress())
                        log.append("LOOPBACK ");
                    log.append(inetAddress.getHostAddress() + "\n");
                }
            }
        } catch (SocketException e) {
            log.append("NetworkInterface.getNetworkInterfaces(): " + e.toString() + "\n");
        }
    }

    protected void listSensors() {
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        log.append("Sensores:\n");
        for (Sensor sensor : sensors)
            log.append(sensor.getName() + " (" + sensor.getVendor() + ")\n");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        log = (TextView) findViewById(R.id.log);
        log.setMovementMethod(new ScrollingMovementMethod());
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        listInterfaces();
        listSensors();

        gps = new GPS(logger, 1414, "gps", this);
        imu = new IMU(logger, 1415, "compass", this);
        proximity = new Proximity(logger, 1416, "proximity", this);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sensores");
        wakeLock.acquire();
    }

    @Override
    protected void onDestroy() {
        gps.unregister();
        imu.unregister();
        proximity.unregister();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
            case Sensor.TYPE_MAGNETIC_FIELD: {
                imu.send(event);
                break;
            }

            case Sensor.TYPE_PROXIMITY: {
                proximity.send(event);
                break;
            }

            default:
            log.append("onSensorChanged: Sensor desconhecido recebido\n");
        }
    }

    @Override
    public void onLocationChanged(Location location) {}

    public void onNmeaReceived(long timestamp, String nmea){
        gps.send(nmea);
    }

    @Override
    public void onProviderEnabled(String provider) {
        logger.add("Provider ativado: " + provider + "\n");
    }


    @Override
    public void onProviderDisabled(String provider) {
        logger.add("Provider perdido: " + provider + "\n");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        int satelites;
        logger.add("Status de " + provider + ": ");
        if (status == LocationProvider.OUT_OF_SERVICE)
            logger.add("Fora de serviço");
        else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
            logger.add("Temporariamente sem sinal");
        else if (status == LocationProvider.AVAILABLE)
            logger.add("Disponível");
        else
            logger.add("Status desconhecido");

        satelites = extras.getInt("satellites", -1);
        if (satelites != -1)
            logger.add(" (" + Integer.toString(satelites) + " satélites)");
        logger.add("\n");
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        log.append(s.getName() + ": acuracia " + Integer.toString(accuracy) + "\n");
    }
}
