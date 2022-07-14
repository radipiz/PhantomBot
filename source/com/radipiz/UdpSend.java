package com.radipiz;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class UdpSend implements AutoCloseable {

    DatagramSocket socket;

    public UdpSend(String hostname, int port) throws UnknownHostException, SocketException {
        InetAddress address = InetAddress.getByName(hostname);
        socket = new DatagramSocket();
        socket.connect(address, port);
    }

    public void sendMessage(String message) throws IOException {
        byte[] buf = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        this.socket.send(dp);
    }

    @Override
    public void close() throws Exception {
        if (this.socket != null && !this.socket.isClosed()) {
            this.socket.close();
        }
    }
}
