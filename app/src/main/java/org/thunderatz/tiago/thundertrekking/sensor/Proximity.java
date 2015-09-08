package org.thunderatz.tiago.thundertrekking.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import org.thunderatz.tiago.thundertrekking.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Proximity extends SensorSocket {
    public Proximity(Logger logger, int target_port, String my_id, Context context) {
        super(logger, target_port, my_id);
        SensorManager mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        SensorEventListener activity = (SensorEventListener) context;
        if (mSensorManager.registerListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_FASTEST))
            logger.add("Com TYPE_PROXIMITY\n");
        else
            logger.add("Sem TYPE_PROXIMITY\n");
    }

    public void send(SensorEvent event) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1); // espaço para 1 float
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(event.values[0]);
        send(buffer.array());
    }
}
