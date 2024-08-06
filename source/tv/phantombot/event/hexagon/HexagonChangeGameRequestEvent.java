package tv.phantombot.event.hexagon;

/**
 * Indicates changes to a running Hexagon game
 */
public class HexagonChangeGameRequestEvent extends HexagonEvent {
    public final Integer gameTime;
    public final String playerName;
    public final Integer level;

    public HexagonChangeGameRequestEvent(String playerName, Integer gameTime, Integer level) {
        this.playerName = playerName;
        this.gameTime = gameTime;
        this.level = level;
    }
}
