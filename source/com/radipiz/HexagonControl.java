package com.radipiz;

import com.gmt2001.Logger;

import java.io.IOException;

public class HexagonControl extends TcpSend {

    private boolean userControlOn = false;
    private int currentSpeed = 0;

    public HexagonControl(String hostname, int port) throws IOException {
        super(hostname, port);
    }

    public void disable() throws HexagonControlError {
        this.setSpeed(0, true);
        this.setUserControlOn(false);
    }

    public void enable() throws HexagonControlError {
        this.setUserControlOn(true);
    }

    public void setSpeed(int speed) throws HexagonControlError {
        this.setSpeed(speed, false);
    }

    public void setSpeed(int speed, boolean force) throws HexagonControlError {
        if (!userControlOn && !force) {
            Logger.instance().log(Logger.LogType.Debug, "HexagonControl is disabled");
            return;
        }
        if (speed < 0 || speed > 255) {
            throw new InvalidSpeedException();
        }
        try {
            this.sendMessage("M106 S" + speed + "\n");
            this.currentSpeed = speed;
        } catch (IOException e) {
            throw new HexagonControlError("Could not set speed", e);
        }
    }

    public boolean isUserControlOn() {
        return userControlOn;
    }

    public void setUserControlOn(boolean userControlOn) {
        Logger.instance().log(Logger.LogType.Debug, "User HexagonControl: " + userControlOn);
        this.userControlOn = userControlOn;
    }

    public int getCurrentSpeed() {
        return this.currentSpeed;
    }

    private static class InvalidSpeedException extends HexagonControlError {
        public InvalidSpeedException() {
            super("Invalid speed");
        }
    }

    public static class HexagonControlError extends Exception {
        public HexagonControlError(String message, Throwable cause) {
            super(message, cause);
        }

        public HexagonControlError(String message) {
            super(message);
        }
    }
}
