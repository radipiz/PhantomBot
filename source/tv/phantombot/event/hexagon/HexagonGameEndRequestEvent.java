package tv.phantombot.event.hexagon;

/**
 * Indicates the requested end of a Hexagon game as well request to abort a game
 */
public class HexagonGameEndRequestEvent extends HexagonEvent {
    public final boolean abort;

    /**
     * Requests a running game to end
     * @param abort if true the game is aborted the "hard" way, and it'll just end without announcing the end
     */
    public HexagonGameEndRequestEvent(final boolean abort) {
        this.abort = abort;
    }
}
