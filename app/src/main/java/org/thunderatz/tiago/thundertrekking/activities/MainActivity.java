package org.thunderatz.tiago.thundertrekking.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;
import org.thunderatz.tiago.thundertrekking.ListenerRegisterer;
import org.thunderatz.tiago.thundertrekking.R;
import org.thunderatz.tiago.thundertrekking.util.Logger;
import org.thunderatz.tiago.thundertrekking.sensors.SensorThread;
import org.thunderatz.tiago.thundertrekking.util.NetworkDump;
import org.thunderatz.tiago.thundertrekking.views.ScrollingTextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener, LocationListener, GpsStatus.Listener,
        CameraBridgeViewBase.CvCameraViewListener2 {

    private class PrefixLogger implements Logger {
        private final String prefix;

        public PrefixLogger(final String prefix) {
            this.prefix = prefix + ": ";
        }

        @Override
        public void add(final String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log.append(prefix + msg + "\n");
                }
            });
        }
    }

    private ScrollingTextView log;
    private SensorManager mSensorManager;
    private float[] gravity = new float[3];
    private SensorThread gps;
    private SensorThread compass;
    private SensorThread proximity;
    private SensorThread camera;
    private SensorEventListener sensor_listener;
    private LocationManager locationManager;
    private int satelites_ultimo = -1, satelites_usados_ultimo = -1;
    private boolean gps_ativado = false;

    private Mat frameGray;
    private CascadeClassifier cascadeDetector;
    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    try {
                        File mCascadeFile;
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_cone);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_cone.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        cascadeDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (cascadeDetector.empty())
                            cascadeDetector = null;

                        cascadeDir.delete();

                    } catch (IOException e) {
                        log.append(e.toString());
                        e.printStackTrace();
                    }
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PackageManager pm;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensor_listener = this;
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationManager.addGpsStatusListener(this);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        log = (ScrollingTextView) findViewById(R.id.log);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        log.append(new NetworkDump().toString());

        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        log.append("Sensores:\n");
        for (Sensor sensor : sensors)
            log.append(sensor.getName() + " (" + sensor.getVendor() + ")\n");

        // GPS pode demorar bastante para receber o primeiro fix (até dois minutos nos testes),
        // então deve ser deixado ligado durante as provas do trekking. Uma opçaõ seria ativá-lo
        // pelo PC do trekking enviando um pacote para a thread do GPS e não desativá-lo ao final do
        // programa, mas preferi deixar sempre ligado no celular e usar o pacote apenas para configurar
        // a porta que recebe as leituras. Para todos os outros sensores, ListenerRegisterer.register()
        // ativa o sensor e unregister() desativa entre os usos para salvar bateria
        final String provider;
        Criteria criteria = new Criteria();
        // Ver http://developer.android.com/reference/android/location/Criteria.html
        // Podemos também pedir dados como velocidade
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        provider = locationManager.getBestProvider(criteria, false);
        if (provider != null) {
            log.append("gps: provider " + provider + "\n");
            locationManager.requestLocationUpdates(provider, 0, 0, this);
        } else
            log.append("gps: sem gps\n");
        gps = new SensorThread(new PrefixLogger("gps (1414)"), 1414, new ListenerRegisterer() {
            @Override
            public boolean register() {
                if (provider == null)
                    return false;
                gps_ativado = true;
                return true;
            }

            @Override
            public void unregister() {
                gps_ativado = false;
            }
        });

        final PrefixLogger compassLogger =  new PrefixLogger("compass (1415)");
        compass = new SensorThread(compassLogger, 1415, new ListenerRegisterer() {
            @Override
            public boolean register() {
                boolean acelerometro_necessario = false;
                // TYPE_ROTATION_VECTOR retorna rotação como mix do campo magnético e giroscópio
                // (usando campo magnético para leitura da rotação, mas calculando com giroscópio a rotação
                // entre as amostras do campo magnético e permitindo maior frequência de atualização que apenas
                // com campo magnético) e será nosso sensor preferido. Tem maior consumo de bateria também
                if (mSensorManager.registerListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST)) {
                    compassLogger.add("Com TYPE_ROTATION_VECTOR\n");
                } else {
                    // Sem giroscópio
                    compassLogger.add("Sem TYPE_ROTATION_VECTOR\n");
                    if (mSensorManager.registerListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST)) {
                        // Com leitura do campo magnético terrestre
                        compassLogger.add("Com TYPE_GEOMAGNETIC_ROTATION_VECTOR\n");
                    } else {
                        compassLogger.add("Sem TYPE_GEOMAGNETIC_ROTATION_VECTOR\n");
                        if (mSensorManager.registerListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST)) {
                            compassLogger.add("Com TYPE_MAGNETIC_FIELD\n");
                            acelerometro_necessario = true;
                        } else {
                            compassLogger.add("Sem TYPE_MAGNETIC_FIELD\n");
                        }
                    }
                }

                if (acelerometro_necessario)
                    if (mSensorManager.registerListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL))
                        compassLogger.add("Com TYPE_ACCELEROMETER\n");
                    else {
                        compassLogger.add("Sem TYPE_ACCELEROMETER\n");
                        mSensorManager.unregisterListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
                        return false;
                    }
                return true;
            }

            @Override
            public void unregister() {
                mSensorManager.unregisterListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
                mSensorManager.unregisterListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR));
                mSensorManager.unregisterListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR));
                mSensorManager.unregisterListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
                mSensorManager.unregisterListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            }
        });
        final PrefixLogger proximityLogger = new PrefixLogger("proximity (1416)");
        proximity = new SensorThread(proximityLogger, 1416, new ListenerRegisterer() {
            @Override
            public boolean register() {
                if (mSensorManager.registerListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_FASTEST)) {
                    proximityLogger.add("Com TYPE_PROXIMITY\n");
                    return true;
                }
                proximityLogger.add("Sem TYPE_PROXIMITY\n");
                return false;
            }

            @Override
            public void unregister() {
                mSensorManager.unregisterListener(sensor_listener, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY));
            }
        });

        camera = new SensorThread(new PrefixLogger("camera (1417)"), 1417, new ListenerRegisterer() {
            @Override
            public boolean register() {
                mOpenCvCameraView.enableView();
                return true;
            }

            @Override
            public void unregister() {
                mOpenCvCameraView.disableView();
            }
        });
        pm = getPackageManager();
        if (pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).size() > 0) {

        } else
            log.append("Sem ACTION_RECOGNIZE_SPEECH\n");
        /*
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sensores");
        wakeLock.acquire();
        */
        //  wakelock.release() quando não houver ninguém conectado

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        //mOpenCvCameraView.enableView();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug())
            // local library
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, loaderCallback);
        else
            // system library
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
    }
    /*
    @Override
    protected void onPause() {
        super.onPause();
        // Parar de receber leituras de sensores para economizar bateria
        mSensorManager.unregisterListener(this);
        // Parar câmera
        //if (mOpenCvCameraView != null)
        //    mOpenCvCameraView.disableView();
    }
    */

    @Override
    protected void onDestroy() {
        gps.close();
        compass.close();
        proximity.close();
        camera.close();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        frameGray = new Mat();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                log.append("onCameraFrame\n");
            }
        });

        if (cascadeDetector == null)
            return null;

        frameGray = inputFrame.gray();
        MatOfRect hits = new MatOfRect();
        cascadeDetector.detectMultiScale(frameGray, hits, 1.1, 2, 2,
                new Size(100, 150), new Size());

        Rect[] hitsArray = hits.toArray();
        ByteBuffer buffer = ByteBuffer.allocate(hitsArray.length * 4 * 8); // espaço para 3 floats
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (Rect rectangle : hitsArray) {
            buffer.putDouble(rectangle.tl().x);
            buffer.putDouble(rectangle.tl().y);
            buffer.putDouble(rectangle.br().x);
            buffer.putDouble(rectangle.br().y);
        }
        camera.send(buffer.array());
        return null;
    }

    @Override
    public void onCameraViewStopped() {
        frameGray.release();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER: {
                boolean vazio;
                vazio = true;
                for (float n : gravity)
                    if (n != 0.) {
                        vazio = false;
                        break;
                    }

                if (vazio)
                    gravity = Arrays.copyOf(event.values, 3);
                else
                    for (int i = 0; i < 3; i++)
                        gravity[i] = 0.8f * gravity[i] + (1.f - 0.8f) * event.values[i];

                break;
            }

            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR: {
                // Isso nunca foi testado, não tenho celular com esses sensores.
                // Para quem quiser usar:
                // http://developer.android.com/reference/android/hardware/SensorEvent.html#values
                // São melhores que TYPE_MAGNETIC_FIELD e parecem mais fáceis de programar

                float[] rotation = new float[9];
                float[] orientacao_celular = new float[3];
                SensorManager.getRotationMatrixFromVector(rotation, event.values);
                SensorManager.getOrientation(rotation, orientacao_celular);
                break;
            }

            case Sensor.TYPE_MAGNETIC_FIELD: {
                // Enviaremos valores diretamente do sensor de campo magnético e faremos processamento no
                // computador (o cálculo de orientação do Android está meio foda de usar)
                float[] rotacao = new float[9], inclinacao = new float[9], orientacao_celular = new float[3];
                // float inclinacao_geomagnetico;
                boolean vazio;
                // Não enviar leituras enquanto não tivermos uma do acelerômetro
                vazio = true;
                for (float n : gravity) {
                    if (n != 0) {
                        vazio = false;
                        break;
                    }
                }
                if (vazio)
                    return;
                // getRotationMatrix retorna false se houver erro (matriz próxima de nula, por
                // exemplo, em queda livre). Também se não tivermos recebido nenhuma leitura
                // do acelerômetro ainda
                if (SensorManager.getRotationMatrix(rotacao, inclinacao, gravity, event.values)) {
                    ByteBuffer buffer = ByteBuffer.allocate(4 * 3); // espaço para 3 floats
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    SensorManager.getOrientation(rotacao, orientacao_celular);
                    for (float value : orientacao_celular) {
                        buffer.putFloat(value);
                    }
                    // Log.i("compass", "reading " + buffer.getFloat(0) + "," + buffer.getFloat(4) + "," + buffer.getFloat(8));
                    compass.send(buffer.array());
                    // A seguinte linha pode servir para pegar inclinação do campo
                    // inclinacao_geomagnetico = SensorManager.getInclination(inclinacao);

                    // E para testar os valores:
                    /*log.setText("Compass: yaw: " + Double.toString(orientacao_celular[0] * 180.0f / Math.PI) +
                            "\npitch: " + Double.toString(orientacao_celular[1] * 180.0f / Math.PI) +
                            "\nroll: " + Double.toString(orientacao_celular[2] * 180.0f / Math.PI) +
                            "\nincl: " + Double.toString(inclinacao_geomagnetico * 180.0f / Math.PI));*/
                }
                break;
            }

            case Sensor.TYPE_PROXIMITY: {
                ByteBuffer buffer = ByteBuffer.allocate(4 * 1); // espaço para 1 float
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putFloat(event.values[0]);
                proximity.send(buffer.array());
                break;
            }

            default:
            log.append("onSensorChanged: Sensor desconhecido recebido\n");
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (gps_ativado) {
            ByteBuffer buffer = ByteBuffer.allocate(8 * 2); // espaço para 2 doubles
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putDouble(location.getLatitude());
            buffer.putDouble(location.getLongitude());
            gps.send(buffer.array());
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        log.append("Provider ativado: " + provider + "\n");
    }


    @Override
    public void onProviderDisabled(String provider) {
        log.append("Provider perdido: " + provider + "\n");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        int satelites;
        log.append("Status de " + provider + ": ");
        if (status == LocationProvider.OUT_OF_SERVICE)
            log.append("Fora de serviço");
        else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
            log.append("Temporariamente sem sinal");
        else if (status == LocationProvider.AVAILABLE)
            log.append("Disponível");
        else
            log.append("Status desconhecido");

        satelites = extras.getInt("satellites", -1);
        if (satelites != -1)
            log.append(" (" + Integer.toString(satelites) + " satélites)");
        log.append("\n");
    }

    @Override
    public void onGpsStatusChanged(int event) {
        GpsStatus status = locationManager.getGpsStatus(null);
        int satelites = 0, satelites_usados = 0;
        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                log.append("gps: iniciado\n");
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                log.append("gps: parado\n");
                break;

            case GpsStatus.GPS_EVENT_FIRST_FIX:
                log.append("gps: first fix (" + Integer.toString(status.getTimeToFirstFix()) + " ms)\n");
                break;

            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                // GpsSatellite fornece vários dados interessantes, podemos buscar mais coisas aqui
                for (GpsSatellite sat : status.getSatellites()) {
                    if(sat.usedInFix())
                        satelites_usados++;
                    satelites++;
                }
                if (satelites_usados != satelites_usados_ultimo || satelites != satelites_ultimo) {
                    log.append("gps: " + Integer.toString(satelites) + " detectados, " + Integer.toString(satelites_usados) + " usados para fixar\n");
                    satelites_usados_ultimo = satelites_usados;
                    satelites_ultimo = satelites;
                }
            break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        log.append(s.getName() + ": acuracia " + Integer.toString(accuracy) + "\n");
    }
}
