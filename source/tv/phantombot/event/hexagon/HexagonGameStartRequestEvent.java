package tv.phantombot.event.hexagon;

/**
 * Indicates the requested start of a Hexagon game
 */
public class HexagonGameStartRequestEvent extends HexagonEvent {
    public final int gameTime;
    public final String playerName;
    public final int level;

    public HexagonGameStartRequestEvent(String playerName) {
        this(playerName, -1);
    }

    public HexagonGameStartRequestEvent(String playerName, int level) {
        this(playerName, level, -1);
    }

    public HexagonGameStartRequestEvent(String playerName, int level, int gameTime) {
        this.playerName = playerName;
        this.level = level;
        this.gameTime = gameTime;
    }
}
