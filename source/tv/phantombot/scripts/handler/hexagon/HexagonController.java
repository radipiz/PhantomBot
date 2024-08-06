package tv.phantombot.scripts.handler.hexagon;

import org.apache.commons.lang3.StringUtils;
import tv.phantombot.PhantomBot;
import tv.phantombot.event.Event;
import tv.phantombot.event.EventBus;
import tv.phantombot.event.hexagon.*;
import tv.phantombot.event.irc.message.IrcChannelMessageEvent;
import tv.phantombot.script.ScriptEventHandler;
import tv.phantombot.script.ScriptEventManager;
import tv.phantombot.twitch.irc.TwitchSession;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class HexagonController implements ScriptEventHandler {

    private final Timer gameTimer = new Timer("Hexagon Gametimer");
    /**
     * Game time left in seconds
     * Negative if no game is running
     */
    private int gameTimeLeft = -1;
    private String currentPlayerName;
    protected final HexagonConfig config = new HexagonConfig();
    protected final HexagonBridge hexagon;
    protected HexagonState lastKnownGoodState = HexagonState.READY;
    private TimerTask currentGameTimer;
    private boolean useStderrInsteadOfChat = false;

    public HexagonController() {
        config.configurationCheck();
        hexagon = new HexagonBridge(config.getHost(), config.getPort());
        hexagon.setStateChangedEventHandler(this::emitStateUpdate);
        registerEventHandlers();
        hexagon.setEnableConnect(true);
    }

    /**
     * Registers event handlers to prepare the HexagonController.
     * This registers only the basic events. A running game needs additional events.
     */
    private void registerEventHandlers() {
        ScriptEventManager.instance().register("hexagonGameStartRequest", this);
        ScriptEventManager.instance().register("hexagonGameEndRequest", this);
        ScriptEventManager.instance().register("hexagonChangeGameRequest", this);
    }

    private void registerGameEventHandlers() {
        // register handler to receive ircChannelMessages to process chat
        ScriptEventManager.instance().register("ircChannelMessage", this);
    }

    protected void emitStateUpdate(HexagonState newState) {
        // Restore the active game
        if (newState == HexagonState.READY && lastKnownGoodState == HexagonState.GAME_ACTIVE) {
            EventBus.instance().postAsync(new HexagonStateEvent(HexagonState.GAME_ACTIVE));
            return;
        }
        if (List.of(HexagonState.READY, HexagonState.GAME_ACTIVE).contains(newState)) {
            lastKnownGoodState = newState;
        }
        EventBus.instance().post(new HexagonStateEvent(newState));
    }

    protected void sendMsg(String message) {
        if (useStderrInsteadOfChat) {
            System.err.println(message);
        } else {
            PhantomBot.instance().getSession().sayNow(message);
        }
    }

    protected void emitMessage(String message) {
        EventBus.instance().post(new HexagonMessageEvent(message));
    }

    @Override
    public void handle(Event event) {
        try {
            if (event instanceof IrcChannelMessageEvent) {
                handleMessageEvent((IrcChannelMessageEvent) event);
            } else if (event instanceof HexagonGameStartRequestEvent) {
                handleStartRequest((HexagonGameStartRequestEvent) event);
            } else if (event instanceof HexagonGameEndRequestEvent) {
                handleEndRequest((HexagonGameEndRequestEvent) event);
            } else if (event instanceof HexagonChangeGameRequestEvent) {
                handleChangeRequest((HexagonChangeGameRequestEvent) event);
            } else {
                com.gmt2001.Console.warn.println(String.format("Received event %s (ignoring)", event.getClass()));
            }
        } catch (HexagonException | IllegalStateException ex) {
            emitMessage(ex.getClass().getSimpleName() + ": " + ex);
            com.gmt2001.Console.err.printStackTrace(ex);
        }
    }

    protected void handleMessageEvent(IrcChannelMessageEvent message) {
        if (!isGameRunning()) {
            // ignore everything if the game is running
            // event shouldn't be registered in that case
            return;
        }
        if (!StringUtils.equalsAnyIgnoreCase(currentPlayerName, message.getSender())) {
            // ignore all messages not by the player
            return;
        }
        if (message.getMessage().toLowerCase().startsWith(config.getStopCommand())) {
            endGame();
        }
    }

    /**
     * Changes settings of the Hexagon. Speed can be set at any time but other game related parameters
     * don't make sense because they are overwritten at game start.
     *
     * @param changeRequest a change request with the new settings
     */
    protected void handleChangeRequest(HexagonChangeGameRequestEvent changeRequest) throws HexagonException {
        if (changeRequest.level >= 0) {
            if (10 >= changeRequest.level) {
                try {
                    hexagon.setLevel(changeRequest.level);
                } catch (HexagonBridgeException e) {
                    throw new HexagonException("Couldn't set level", e);
                }
            } else {
                throw new IllegalArgumentException("Invalid level: " + changeRequest.level);
            }
        }
        if (!StringUtils.isEmpty(changeRequest.playerName)) {
            currentPlayerName = changeRequest.playerName;
        }
        if (changeRequest.gameTime > 0) {
            gameTimeLeft = changeRequest.gameTime;
        }
        EventBus.instance().postAsync(new HexagonMessageEvent(String.format(
                "Changed parameters: level=%s, player=%s, gameTime=%s",
                changeRequest.level, currentPlayerName, gameTimeLeft
        )));
    }

    protected void handleEndRequest(HexagonGameEndRequestEvent endRequest) {
        if (isGameRunning()) {
            if (endRequest.abort) {
                abortGame();
            } else {
                endGame();
            }
        } else {
            throw new IllegalStateException("Game end requested but no game is currently running");
        }
    }

    protected void handleStartRequest(HexagonGameStartRequestEvent startRequestedEvent) {
        if (isGameRunning()) {
            EventBus.instance().postAsync(new HexagonMessageEvent("A game is already active."));
            return;
        }
        if (StringUtils.isEmpty(startRequestedEvent.playerName)) {
            EventBus.instance().postAsync(new HexagonMessageEvent("Cannot start the game without a player name."));
            return;
        }
        // If the requested game time is 0 or negative, use the default time
        if (0 >= startRequestedEvent.gameTime) {
            startGame(startRequestedEvent.playerName);
        } else {
            startGame(startRequestedEvent.playerName, startRequestedEvent.gameTime);
        }
    }

    /**
     * Starts a new game round of the Hexagon
     *
     * @param playerName the name of the player. Only the player can can use the stop command
     */
    public void startGame(String playerName) {
        startGame(playerName, config.getGameTime());
    }

    public void startGame(final String playerName, final int gameTime) {
        startGame(playerName, gameTime, config.getDefaultLevel());
    }

    /**
     * Starts a new game round of the Hexagon
     *
     * @param gameTime   the length of the game in seconds
     * @param playerName the name of the player. Only the player can can use the stop command
     * @param level      the level (speed) the Hexagon should run at: 0-10 possible
     */
    public void startGame(final String playerName, final int gameTime, final int level) {
        try {
            gameTimeLeft = gameTime;
            currentPlayerName = playerName;
            registerGameEventHandlers();
            // turn on the Hexagon!
            hexagon.setLevel(level);
            emitStateUpdate(HexagonState.GAME_ACTIVE);
            sendMsg(config.getGameStartMessage(playerName, gameTime));
            currentGameTimer = new TimerTask() {
                @Override
                public void run() {
                    System.err.println("Gametime left: " + gameTimeLeft);
                    gameTimeLeft--;
                    if (gameTimeLeft > 60) {
                        if (gameTimeLeft % 30 == 0) {
                            sendMsg(config.getTimeAnnounceMessage(gameTimeLeft));
                        }
                    } else if (gameTimeLeft > 10) {
                        if (gameTimeLeft % 15 == 0) {
                            sendMsg(config.getTimeAnnounceMessage(gameTimeLeft));
                        }
                    } else if (gameTimeLeft == 10) {
                        sendMsg(config.getTimeAnnounceMessage(gameTimeLeft));
                    } else if (gameTimeLeft > 0) {
                        sendMsg(config.getTimeShortAnnounceMessage(gameTimeLeft));
                    } else {
                        // Automatically end the game after the set game time
                        endGame();
                    }
                }
            };
            gameTimer.scheduleAtFixedRate(currentGameTimer, 0, 1000);
        } catch (HexagonBridgeException ex) {
            cleanup();
        }
    }

    /**
     * Silently stops the game and resets all values.
     * No message is sent.
     */
    public void abortGame() {
        com.gmt2001.Console.warn.println("Hexagon game round cancelled (placeholder message)");
        try {
            // end the timer first to avoid any further actions
            currentGameTimer.cancel();
            //gameTimer.cancel();
            // turn off the Hexagon
            hexagon.setLevel(0);
            emitStateUpdate(HexagonState.READY);
        } catch (HexagonBridgeException e) {
            emitStateUpdate(HexagonState.ERROR);
            com.gmt2001.Console.err.println("Hexagon abort failed: " + e);
        } finally {
            cleanup();
        }
    }

    protected void cleanup() {
        // remove the message listener
        ScriptEventManager.instance().unregister(this);
        registerEventHandlers();
        // reset variables to safe values
        gameTimeLeft = -1;
    }

    /**
     * Ends the game round and does cleanup actions
     */
    public void endGame() {
        // call abort because it does everything
        abortGame();
        // announce the end
        sendMsg(config.getGameEndMessage());
    }

    public boolean isGameRunning() {
        return gameTimeLeft >= 0;
    }
}
