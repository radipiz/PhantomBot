package com.radipiz;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class TcpSend implements AutoCloseable {

    ServerSocket socket;
    Socket clientSocket;
    Thread acceptorThread;

    public TcpSend(String hostname, int port) throws IOException {
        socket = new ServerSocket(port);
        this.acceptorThread = new ClientHandler(this.socket, this);
        this.acceptorThread.start();
    }

    public void sendMessage(String message) throws IOException {
        if (this.isClientConnected()) {
            try {
                byte[] buf = message.getBytes(StandardCharsets.UTF_8);
                this.clientSocket.getOutputStream().write(buf);
            } catch (SocketException ex) {
                com.gmt2001.Console.warn.println("Lost connection to Hexagon client");
                this.clientSocket.close();
                this.clientSocket = null;
                throw ex;
            }
        } else {
            com.gmt2001.Console.err.println("Tried to send message but no client is connected");
        }
    }

    public boolean isClientConnected() {
        return this.clientSocket != null && this.clientSocket.isConnected();
    }

    @Override
    public void close() throws Exception {
        this.acceptorThread.interrupt();
        if (this.socket != null && !this.socket.isClosed()) {
            this.socket.close();
        }
        if (this.clientSocket != null && !this.clientSocket.isClosed()) {
            this.clientSocket.close();
        }
    }

    protected void setClientSocket(Socket client) throws IOException {
        this.clientSocket = client;
    }

    static class ClientHandler extends Thread {
        public boolean keepRunning = true;
        private final ServerSocket server;
        private final TcpSend target;

        public ClientHandler(ServerSocket server, TcpSend target) {
            this.server = server;
            this.target = target;
        }

        @Override
        public void run() {
            com.gmt2001.Console.debug.println("Started Hexagon ClientHandler");
            while (keepRunning) {
                if (!this.target.isClientConnected()) {
                    try {
                        Socket client = this.server.accept();
                        com.gmt2001.Console.debug.println("Accepted Hexagon Client " + client);
                        this.target.setClientSocket(client);
                    } catch (IOException e) {
                        this.keepRunning = false;
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    com.gmt2001.Console.debug.println("Ending Hexagon Acceptor thread");
                    return;
                }
            }
        }
    }
}
