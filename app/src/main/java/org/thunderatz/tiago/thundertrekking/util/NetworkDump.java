package org.thunderatz.tiago.thundertrekking.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

///@TODO: StringBuilder
public class NetworkDump {
    public String toString() {
        String dump = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();
                    dump += "if " + networkInterface.getName() + " (" + networkInterface.getDisplayName();
                    if (inetAddress.isLoopbackAddress())
                        dump += " LOOPBACK";
                    dump += "):";
                    dump += inetAddress.getHostAddress() + "\n";
                }
            }
        } catch (SocketException e) {
            dump += "\nNetworkInterface.getNetworkInterfaces(): " + e.toString() + "\n";
        }
        return dump;
    }
}
