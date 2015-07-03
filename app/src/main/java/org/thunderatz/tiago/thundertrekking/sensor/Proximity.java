package org.thunderatz.tiago.thundertrekking.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import org.thunderatz.tiago.thundertrekking.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Proximity extends SensorThread {
    SensorManager mSensorManager;
    SensorEventListener activity;
    public Proximity(Logger l, int target_port, String my_id, Context context) {
        super(l, target_port, my_id);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        activity = (SensorEventListener) context;
    }

    @Override
    public boolean register() {
        if (mSensorManager.registerListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_FASTEST)) {
            logger.add("Com TYPE_PROXIMITY\n");
            return true;
        }
        logger.add("Sem TYPE_PROXIMITY\n");
        return false;
    }

    @Override
    public void unregister() {
        mSensorManager.unregisterListener(activity, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY));
    }

    public void send(SensorEvent event) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1); // espaço para 1 float
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(event.values[0]);
        send(buffer.array());
    }
}
