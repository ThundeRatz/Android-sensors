package org.thunderatz.tiago.thundertrekking.sensors;

import org.thunderatz.tiago.thundertrekking.ListenerRegisterer;
import org.thunderatz.tiago.thundertrekking.util.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SensorThread extends Thread {
    int port;
    protected DatagramSocket socket;
    protected Logger logger;
    protected InetAddress client_addr;
    protected int client_port = 0;
    protected ListenerRegisterer registerer;

    public SensorThread(Logger logger, int target_port, ListenerRegisterer r) {
        this.logger = logger;
        port = target_port;
        registerer = r;
        start();
    }

    @Override
    public void run() {
        if (registerer != null) {
            try {
                socket = new DatagramSocket(port);
                byte[] remote_port = new byte[5];

                while (socket != null) {
                    DatagramPacket packet = new DatagramPacket(remote_port, 5);
                    int new_port;
                    logger.add("Esperando conexões");
                    socket.receive(packet);
                    String remote = new String(remote_port, 0, packet.getLength()).trim();
                    client_addr = packet.getAddress();

                    // Pacote vazio para sensores
                    if (remote.isEmpty()) {
                        registerer.unregister();
                        logger.add("Parando transmissão");
                        continue;
                    }

                    try {
                        client_port = Integer.parseInt(new String(remote_port).trim());
                    } catch (NumberFormatException e) {
                        logger.add(e.toString());
                        continue;
                    }

                    logger.add(client_addr.getHostName() + ":" + Integer.toString(packet.getPort()) + " direcionando para " + Integer.toString(client_port));
                    if (registerer != null) {
                        // Se não pudemos registrar (sensor não existe), enviar pacote vazio para clientes
                        // indicando que não temos o sensor
                        if (!registerer.register()) {
                            logger.add("registerer.register retornou erro\n");
                            DatagramPacket empty;
                            empty = new DatagramPacket(null, 0, client_addr, client_port);
                            try {
                                socket.send(empty);
                            } catch (IOException e) {
                                logger.add(e.toString());
                                client_port = 0;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.add(e.toString());
                close();
            }
            logger.add("saindo da thread");
        }
    }

    public void send(byte[] data) {
        if (client_port == 0) {
            logger.add("send: Sem clientes");
            return;
        }

        final DatagramPacket packet = new DatagramPacket(data, data.length, client_addr, client_port);
        new Thread(new Runnable() {
            public void run() {
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    logger.add(e.toString());
                }
            }
        }).start();
    }

    public void close() {
        logger.add("close");
        if (registerer != null) {
            registerer.unregister();
            socket.close();
            socket = null;
            client_port = 0;
        }
    }
}
