package tv.phantombot.event.hexagon;

/**
 * Event to communicate (informal) messages
 */
public class HexagonMessageEvent extends HexagonEvent {
    public final String message;

    public HexagonMessageEvent(String msg){
        this.message = msg;
    }
}
