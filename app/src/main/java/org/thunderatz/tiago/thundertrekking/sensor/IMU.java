package org.thunderatz.tiago.thundertrekking.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import org.thunderatz.tiago.thundertrekking.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class IMU extends SensorSocket {
    private static final float LOW_PASS_ALPHA = 0.85f;
    private float[] gravity = new float[] {0.f, 0.f, 0.f};

    public IMU(Logger l, int target_port, String my_id, Context context) {
        super(l, target_port, my_id);
        SensorManager mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        SensorEventListener activity = (SensorEventListener) context;

        // TYPE_ROTATION_VECTOR retorna rotação como mix do campo magnético e giroscópio
        // (usando campo magnético para leitura da rotação, mas calculando com giroscópio a rotação
        // entre as amostras do campo magnético e permitindo maior frequência de atualização que apenas
        // com campo magnético) e será nosso sensor preferido. Tem maior consumo de bateria também
        if (mSensorManager.registerListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST)) {
            logger.add("Com TYPE_ROTATION_VECTOR\n");
        } else {
            // Sem giroscópio
            logger.add("Sem TYPE_ROTATION_VECTOR\n");
            if (mSensorManager.registerListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST)) {
                // Com leitura do campo magnético terrestre
                logger.add("Com TYPE_GEOMAGNETIC_ROTATION_VECTOR\n");
            } else {
                logger.add("Sem TYPE_GEOMAGNETIC_ROTATION_VECTOR\n");
                if (mSensorManager.registerListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST))
                    logger.add("Com TYPE_MAGNETIC_FIELD\n");
                else
                    logger.add("Sem TYPE_MAGNETIC_FIELD\n");
            }
        }

        if (mSensorManager.registerListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST))
            logger.add("Com TYPE_ACCELEROMETER\n");
        else {
            logger.add("Sem TYPE_ACCELEROMETER\n");
            mSensorManager.unregisterListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        }
        // Seria interessante ter um celular com TYPE_GRAVITY, TYPE_GYROSCOPE e TYPE_LINEAR_ACCELERATION
    }

    public void send(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER: {
                // low-pass
                for (int i = 0; i < 3; i++)
                    gravity[i] = LOW_PASS_ALPHA * gravity[i] + (1.f - LOW_PASS_ALPHA) * event.values[i];
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
                ByteBuffer buffer = ByteBuffer.allocate(4 * 3); // espaço para 3 floats
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                for (float value : orientacao_celular) {
                    buffer.putFloat(value);
                }
                send(buffer.array());
                break;
            }

            case Sensor.TYPE_MAGNETIC_FIELD: {
                float[] rotacao = new float[9], inclinacao = new float[9], orientacao_celular = new float[3];
                // getRotationMatrix retorna false se houver erro (matriz nula, por exemplo, em
                // queda livre). Também se não tivermos recebido nenhuma leitura do acelerômetro ainda
                if (SensorManager.getRotationMatrix(rotacao, inclinacao, gravity, event.values)) {
                    ByteBuffer buffer = ByteBuffer.allocate(4 * 3); // espaço para 3 floats
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    SensorManager.getOrientation(rotacao, orientacao_celular);
                    for (float value : orientacao_celular) {
                        buffer.putFloat(value);
                    }
                    send(buffer.array());
                    // Para teste:
                    /* inclinacao_geomagnetico = SensorManager.getInclination(inclinacao);
                    Log.i("compass", "reading " + buffer.getFloat(0) + "," + buffer.getFloat(4) + "," + buffer.getFloat(8));
                    log.setText("Compass: yaw: " + Double.toString(orientacao_celular[0] * 180.0f / Math.PI) +
                            "\npitch: " + Double.toString(orientacao_celular[1] * 180.0f / Math.PI) +
                            "\nroll: " + Double.toString(orientacao_celular[2] * 180.0f / Math.PI) +
                            "\nincl: " + Double.toString(inclinacao_geomagnetico * 180.0f / Math.PI));*/
                }
                break;
            }
        }
    }
}
