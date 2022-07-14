package com.radipiz;

import tv.phantombot.event.irc.message.IrcChannelMessageEvent;
import tv.phantombot.twitch.irc.TwitchSession;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class HexagonChatControl extends HexagonControl {
    public enum SpeedLevel {
        LEVEL1(50),
        LEVEL2(80),
        LEVEL3(100),
        LEVEL4(120),
        LEVEL5(140),
        LEVEL6(160),
        LEVEL7(180),
        LEVEL8(200),
        LEVEL9(220),
        LEVEL10(240);

        private final int value;
        private static final Map<Integer, SpeedLevel> map = new HashMap<>(10);

        SpeedLevel(int value) {
            this.value = value;
        }

        static {
            for (SpeedLevel pageType : SpeedLevel.values()) {
                SpeedLevel.map.put(pageType.value, pageType);
            }
        }

        public static SpeedLevel valueOf(int speed) {
            return SpeedLevel.map.get(speed);
        }

        public static SpeedLevel getLevel(int level) {
            switch (level) {
                case 1:
                    return LEVEL1;
                case 2:
                    return LEVEL2;
                case 3:
                    return LEVEL3;
                case 4:
                    return LEVEL4;
                case 5:
                    return LEVEL5;
                case 6:
                    return LEVEL6;
                case 7:
                    return LEVEL7;
                case 8:
                    return LEVEL8;
                case 9:
                    return LEVEL9;
                case 10:
                    return LEVEL10;
                default:
                    throw new IllegalArgumentException("Invalid level. Must be between 1 and 10");
            }
        }

        public int getValue() {
            return value;
        }
    }

    private String allowedUser;
    private TwitchSession twitch;
    private Timer gameTimer;
    private int gameTimeInSeconds = 45;
    private boolean isGameRunning = false;

    public HexagonChatControl(String hostname, int port) throws SocketException, UnknownHostException {
        super(hostname, port);
    }

    public void startGameRound(String player, SpeedLevel level) throws HexagonControlError {
        if(this.isGameRunning){
            throw new IllegalStateException("A game round is already running");
        }
        this.gameTimer = new Timer("HexagonGameTimer");
        this.gameTimer.scheduleAtFixedRate(new GameTimer(this, this.getGameTimeInSeconds()), 1000, 1000L);
        this.allowedUser = player;
        this.enable();
        this.setSpeed(level.getValue());
        this.message(String.format("Meine Damen & Herren: DAS HEXAGON! Spieler @%s, bitte schreibe !stop um das Hexagon anzuhalten. Die Spielzeit beträgt %d Sekunden!", player, this.getGameTimeInSeconds()));
        this.isGameRunning = true;
    }

    public void processStop(IrcChannelMessageEvent event) {
        if (this.isGameRunning && event.getSender().equalsIgnoreCase(this.allowedUser)) {
            this.endRound();
            this.message(String.format("@%s hat entschieden!", event.getSender()));
        }
    }

    public void endRound() {
        try {
            this.isGameRunning = false;
            this.message("AUS! Das Hexagon hält an!");
            this.disable();
            try {
                this.gameTimer.cancel();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        } catch (HexagonControlError e) {
            this.message("Es gab ein technisches Problem. Bitte bleiben Sie sitzen.");
            e.printStackTrace();
        }
    }

    public void reset(){
        try {
            this.disable();
        } catch (HexagonControlError e) {
            e.printStackTrace();
        }
        this.gameTimer.cancel();
        this.isGameRunning = false;
    }

    protected void message(String msg) {
        if (this.twitch != null) {
            this.twitch.send(msg);
        }
    }

    public void setTwitch(TwitchSession twitch) {
        this.twitch = twitch;
    }

    public int getGameTimeInSeconds() {
        return gameTimeInSeconds;
    }

    public void setGameTimeInSeconds(int gameTimeInSeconds) {
        if (gameTimeInSeconds <= 0) {
            throw new IllegalArgumentException("Game Time cannot be 0 or negative");
        }
        this.gameTimeInSeconds = gameTimeInSeconds;
    }


    static class GameTimer extends TimerTask {
        private final HexagonChatControl hexagon;
        private int secondsLeft;

        public GameTimer(HexagonChatControl hexagon, int seconds) {
            assert seconds > 0;
            this.secondsLeft = seconds;
            this.hexagon = hexagon;
        }

        @Override
        public void run() {
            this.secondsLeft--;
            if (this.secondsLeft == 0) {
                this.hexagon.endRound();
            } else if (this.secondsLeft <= 5) {
                this.hexagon.message(String.format("%d!", this.secondsLeft));
            } else {
                if (this.secondsLeft % 10 == 0) {
                    this.hexagon.message(String.format("Noch %d Sekunden!", this.secondsLeft));
                }
            }
            // Abort in case of invalid state
            if(this.secondsLeft < 0){
                this.hexagon.reset();
            }
        }
    }
}
