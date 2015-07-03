package org.thunderatz.tiago.thundertrekking.sensor;

import org.thunderatz.tiago.thundertrekking.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public abstract class SensorThread extends Thread {
    int port;
    String id;
    protected DatagramSocket socket;
    protected Logger logger;
    protected InetAddress client_addr;
    protected int client_port = 0;

    protected SensorThread(Logger l, int target_port, String my_id) {
        logger = l;
        port = target_port;
        id = my_id + "(" + Integer.toString(port) + "): ";
        start();
    }

    protected void log(String msg) {
        logger.add(id + msg + "\n");
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            byte[] remote_port = new byte[5];

            while (socket != null) {
                DatagramPacket packet = new DatagramPacket(remote_port, 5);
                log("Esperando conexões");
                socket.receive(packet);
                String remote = new String(remote_port, 0, packet.getLength()).trim();
                client_addr = packet.getAddress();

                // Pacote vazio para sensores
                if (remote.isEmpty()) {
                    unregister();
                    log("Parando transmissão");
                    continue;
                }

                try {
                    client_port = Integer.parseInt(new String(remote_port).trim());
                } catch (NumberFormatException e) {
                    log(e.toString());
                    continue;
                }

                log(client_addr.getHostName() + ":" + Integer.toString(packet.getPort()) + " direcionando para " + Integer.toString(client_port));
                // Se não pudemos registrar (sensor não existe), enviar pacote vazio para clientes
                // indicando que não temos o sensor
                if (!register()) {
                    log("register retornou erro\n");
                    DatagramPacket empty;
                    empty = new DatagramPacket(null, 0, client_addr, client_port);
                    try {
                        socket.send(empty);
                    } catch (IOException e) {
                        log(e.toString());
                        client_port = 0;
                    }
                }
            }
        } catch (IOException e) {
            log(e.toString());
            unregister();
        }
        log("saindo da thread");
    }

    protected void send(byte[] data) {
        if (client_port == 0)
            return;
        
        final DatagramPacket packet = new DatagramPacket(data, data.length, client_addr, client_port);
        new Thread(new Runnable() {
            public void run() {
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    log(e.toString());
                }
            }
        }).start();
    }

    public abstract boolean register();
    public abstract void unregister();
}
