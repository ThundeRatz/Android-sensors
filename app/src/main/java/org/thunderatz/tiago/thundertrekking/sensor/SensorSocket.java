package org.thunderatz.tiago.thundertrekking.sensor;

import org.thunderatz.tiago.thundertrekking.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class SensorSocket {
    private class TCPServer extends Thread {
        @Override
        public void run() {
            super.run();
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(clientPort);
            } catch (IOException e) {
                log(e.toString());
                return;
            }
            while (true) {
                try {
                    tcpSocket = serverSocket.accept();
                    tcpSocket.shutdownInput();
                    try {
                        tcpOutputStream = tcpSocket.getOutputStream();
                    } catch (IOException e) {
                        tcpSocket.close();
                        log(e.toString());
                    }
                } catch (IOException e) {
                    log(e.toString());
                }
            }
        }
    }

    protected Logger logger;

    private final static byte[] ip = {(byte) 192, (byte) 168, (byte) 42, (byte) 136};
    private final static InetAddress clientAddress;
    private int clientPort = 0;
    private String id;
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private OutputStream tcpOutputStream = null;
    private Lock dataOrderLock = new ReentrantLock();

    static {
        try {
            clientAddress = InetAddress.getByAddress(ip);
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected SensorSocket(Logger logger, int clientPort, String id) {
        this.logger = logger;
        this.clientPort = clientPort;
        this.id = id;
        try {
            udpSocket = new DatagramSocket();
        } catch (SocketException e) {
            log(e.toString());
        }
        new Thread(new TCPServer()).start();
    }

    protected void log(String msg) {
        logger.add(id);  logger.add(": ");
        logger.add(msg); logger.add("\n");
    }

    protected void send(byte[] data) {
        final DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress, clientPort);
        final byte[] packetData = data;
        Runnable sendTask = new Runnable() {
            @Override
            public void run() {
                dataOrderLock.lock();
                try {
                    udpSocket.send(packet);
                } catch (IOException e) {
                    log(e.toString());
                }
                if (tcpOutputStream != null) {
                    try {
                        tcpOutputStream.write(packetData);
                    } catch (IOException e) {
                        log(e.toString());
                        try {
                            tcpOutputStream.close();
                        } catch (IOException e1) {}
                        try {
                            tcpSocket.close();
                        } catch (IOException e1) {}
                        tcpOutputStream = null;
                    }
                }
                dataOrderLock.unlock();
            }
        };
        new Thread(sendTask).start();
    }
}
