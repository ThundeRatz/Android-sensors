package org.thunderatz.tiago.thundertrekking.sensor;

import org.thunderatz.tiago.thundertrekking.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public abstract class SensorSocket {
    protected final static byte[] ip = {(byte) 192, (byte) 168, (byte) 42, (byte) 136};
    protected final static InetAddress clientAddress;
    static {
        try {
            clientAddress = InetAddress.getByAddress(ip);
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    protected DatagramSocket socket;
    protected Logger logger;
    protected int clientPort = 0;
    protected String id;

    protected SensorSocket(Logger logger, int clientPort, String id) {
        this.logger = logger;
        this.clientPort = clientPort;
        this.id = id;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            log(e.toString());
        }
    }

    protected void log(String msg) {
        logger.add(id);  logger.add(": ");
        logger.add(msg); logger.add("\n");
    }

    protected void send(byte[] data) {
        final DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress, clientPort);
        Runnable sendTask = new Runnable() {
            @Override
            public void run() {
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    log(e.toString());
                }
            }
        };
        new Thread(sendTask).start();
    }
}
