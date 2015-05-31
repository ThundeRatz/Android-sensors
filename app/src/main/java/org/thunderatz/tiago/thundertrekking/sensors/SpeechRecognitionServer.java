package org.thunderatz.tiago.thundertrekking.sensors;


import org.thunderatz.tiago.thundertrekking.util.Logger;

import java.io.IOException;
import java.net.DatagramPacket;

public class SpeechRecognitionServer extends SensorThread {
    SpeechRecognitionServer(Logger logger, int target_port) {
        super(logger, target_port, null);
    }

    @Override
    public void run() {
        logger.add("Pronto para recebimento");
        while (socket != null) {
            try {
                DatagramPacket packet = new DatagramPacket(null, 0);
                socket.receive(packet);

            } catch (IOException e) {
                logger.add(e.toString());
                close();
            }
        }
        logger.add("saindo da thread");
    }
}
