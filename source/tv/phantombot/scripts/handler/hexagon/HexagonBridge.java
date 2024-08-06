package tv.phantombot.scripts.handler.hexagon;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class HexagonBridge {
    public final int BUFFER_SIZE = 128;
    public final int MAX_SPEED = 255;
    public final int MIN_SPEED = 40;
    public final int NUM_LEVELS = 10;
    private final ExponentialBackoff backoff = new ExponentialBackoff(1000, 5 * 60 * 1000, 2);
    private final String host;
    private final int port;
    private Socket socket;
    private boolean stopRequested = false;
    protected BlockingDeque<byte[]> responses = new LinkedBlockingDeque<>(1);
    protected byte[] response = new byte[BUFFER_SIZE];

    protected Consumer<HexagonState> stateChangedEvent;


    public HexagonBridge(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setStateChangedEventHandler(Consumer<HexagonState> consumer) {
        this.stateChangedEvent = consumer;
    }

    private void emitStateUpdate(HexagonState newState) {
        if (stateChangedEvent != null) {
            stateChangedEvent.accept(newState);
        }
    }

    protected String responseToString(byte[] response) {
        int strLen = 0;
        for (; strLen < response.length; strLen++) {
            if (response[strLen] == 0) {
                break;
            }
        }
        return new String(ArrayUtils.subarray(response, 0, strLen), StandardCharsets.UTF_8).trim();
    }

    /**
     * Sets the level (speed) of the Hexagon
     * The actual minimum or maximum in effective duty cycle could be higher or lower as expected
     * due to configuration and Hexagon software to protect the hardware
     *
     * @param level a value between 0 (off) and 10 (max)
     * @throws HexagonBridgeException when a communication issue occurred
     */
    public void setLevel(int level) throws HexagonBridgeException {
        byte dutyCycle = calculateSpeedFromLevel(level);
        System.err.printf("Setting speed to level %s (%s / %s)%n", level, dutyCycle, MAX_SPEED);
        if (socket == null) {
            throw new IllegalStateException("Socket is not connected");
        }
        try {
            socket.getOutputStream().write(new byte[]{0x10, dutyCycle});
            byte[] response = responses.poll(5, TimeUnit.SECONDS);
            if (response != null) {
                System.err.println(responseToString(response));
            }
        } catch (IOException e) {
            throw new HexagonBridgeException("Couldn't receive response", e);
        } catch (InterruptedException e) {
            throw new HexagonBridgeException("Interrupted", e);
        }
    }

    /**
     * Calculates the duty cycle for the controller on the gradient
     *
     * @param level a level from 0 (off) to 10 (max)
     * @return a value between 0 and 255
     */
    public byte calculateSpeedFromLevel(int level) {
        if (level == 0) {
            return 0;
        }
        int clampedLevel = Math.max(0, Math.min(10, level));
        int stepSize = (MAX_SPEED - MIN_SPEED) / (NUM_LEVELS);
        return (byte) (MIN_SPEED + clampedLevel * stepSize);
    }

    /**
     * Establishes a connection to the HexagonBridge server and handles reconnects as
     * well as receiving data
     *
     * @throws IOException on connectivity issues
     */
    private void connect() throws IOException {
        stopRequested = false;
        Thread receiveHandler = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            int readBytes;
            while (!stopRequested) {
                try {
                    // If not connected, try to connect
                    if (socket == null) {
                        socket = new Socket(host, port);
                        socket.setSoTimeout(3000);
                        backoff.reset();
                        emitStateUpdate(HexagonState.READY);
                        System.err.println("Hexagon Bridge connected");
                    }
                    readBytes = socket.getInputStream().read(buffer);
                    //System.err.println("Read " + readBytes + " Bytes");
                    if (0 > readBytes) {
                        socket.close();
                    } else {
                        handleInput(socket.getOutputStream(), buffer);
                    }
                } catch (SocketTimeoutException e) {
                    // void
                    // it'll just try to reconnect
                } catch (IOException e) {
                    // cleanup
                    emitStateUpdate(HexagonState.ERROR);
                    if (socket != null && !socket.isClosed()) {
                        try {
                            socket.close();
                        } catch (Exception ex) {
                            // void
                            // ignore anything that goes wrong here. Shouldn't cause any further harm
                        }
                    }
                    try {
                        long waitingTime = backoff.nextBackoffMillis();
                        System.err.println("IOException. Waiting " + waitingTime + " before retrying to connect");
                        //noinspection BusyWait
                        Thread.sleep(waitingTime);
                    } catch (InterruptedException ex) {
                        // in case the thread got interrupted, expect the stop to be requested
                        Thread.currentThread().interrupt();
                        stopRequested = true;
                    } finally {
                        // make sure the socket is null and clean, so it's clear it must be recreated
                        socket = null;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stopRequested = true;
                }
            }
            // cleanup (again) and outside the loop so it's the final cleanup
            if (socket != null) {
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    socket = null;
                    emitStateUpdate(HexagonState.DISCONNECTED);
                }
            }
        }, "HexagonBridge Connect/Receive Handler");
        receiveHandler.setDaemon(true);
        receiveHandler.start();
    }

    /**
     * Disconnects from the server
     * Alternative to setEnableConnect(false)
     */
    public void disconnect() {
        // maybe it's enough to just set stopRequested to true so
        // the thread handles it and the cleanup
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // void
            }
        }
        socket = null;
        stopRequested = true;
    }

    /**
     * Connects or requests the disconnect from the server
     *
     * @param on true if the connection should be established, false to request a disconnect
     */
    public void setEnableConnect(boolean on) {
        stopRequested = on;
        if (on) {
            try {
                this.connect();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Handles any kind of input from the server such as ping requests
     * The input is offered to the responses queue and discarded if it's already full
     *
     * @param out    the output stream to respond
     * @param buffer the buffer with the received contents
     * @throws IOException          on any issues writing the response
     * @throws InterruptedException in case the thread gets interrupted while offering the data to the queue
     */
    private void handleInput(OutputStream out, byte[] buffer) throws IOException, InterruptedException {
        final byte[] pingMsg = {'p', 'i', 'n', 'g'};
        if (Objects.deepEquals(ArrayUtils.subarray(buffer, 0, 4), pingMsg)) {
            pingMsg[1] = 'o';
            out.write(pingMsg);
            return;
        }
        responses.offer(buffer, 1, TimeUnit.SECONDS);
    }

    static class ExponentialBackoff {
        private final long initialIntervalMillis;
        private final long maxIntervalMillis;
        private final double multiplier;

        private long currentIntervalMillis;
        private int attempt;

        public ExponentialBackoff(long initialIntervalMillis, long maxIntervalMillis, double multiplier) {
            this.initialIntervalMillis = initialIntervalMillis;
            this.maxIntervalMillis = maxIntervalMillis;
            this.multiplier = multiplier;
            reset();
        }

        public void reset() {
            currentIntervalMillis = initialIntervalMillis;
            attempt = 0;
        }

        public long nextBackoffMillis() {
            if (attempt > 0) {
                currentIntervalMillis = Math.min(
                        (long) (currentIntervalMillis * multiplier),
                        maxIntervalMillis
                );
            }
            attempt++;

            return currentIntervalMillis;
        }
    }
}
