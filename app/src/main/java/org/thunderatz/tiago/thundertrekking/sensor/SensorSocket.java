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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
                Socket tcpSocket;
                try {
                    tcpSocket = serverSocket.accept();
                } catch (IOException e) {
                    log(e.toString());
                    try {
                        serverSocket.close();
                    } catch (IOException closeException) {
                        log(closeException.toString());
                    }
                    return;
                }
                try {
                    OutputStream tcpOutputStream = tcpSocket.getOutputStream();
                    new Thread(new ClientHandler(tcpOutputStream)).start();
                } catch (IOException e) {
                    log(e.toString());
                }
            }
        }
    }

    private class ClientHandler extends Thread {
        private final OutputStream outputStream;

        ClientHandler(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void run() {
            super.run();

            try {
                while (true) {
                    lock.lock();
                    newPacketCondition.await();
                    lock.unlock();
                    outputStream.write(lastMessage);
                }
            } catch (InterruptedException | IOException e) {
                log(e.toString());
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log(e.toString());
                }
                return;
            }
        }
    }

    protected final static byte[] ip = {(byte) 192, (byte) 168, (byte) 42, (byte) 136};
    protected final static InetAddress clientAddress;
    protected DatagramSocket udpSocket;
    protected Logger logger;
    protected int clientPort = 0;
    protected String id;

    private final Lock lock = new ReentrantLock();
    private final Condition newPacketCondition = lock.newCondition();
    private byte[] lastMessage;
    private TCPServer tcpServer;

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
        /// @FIXME race condition
        lastMessage = data;
        lock.lock();
        newPacketCondition.signalAll();
        lock.unlock();
        Runnable sendTask = new Runnable() {
            @Override
            public void run() {
                try {
                    udpSocket.send(packet);
                } catch (IOException e) {
                    log(e.toString());
                }
            }
        };
        new Thread(sendTask).start();
    }
}
