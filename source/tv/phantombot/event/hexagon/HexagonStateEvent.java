package tv.phantombot.event.hexagon;

import tv.phantombot.scripts.handler.hexagon.HexagonState;

/**
 * Event to communicate results of state changes of the Hexagon
 */
public class HexagonStateEvent extends HexagonEvent {
    public final HexagonState state;

    public HexagonStateEvent(HexagonState state){
        this.state = state;
    }
}
