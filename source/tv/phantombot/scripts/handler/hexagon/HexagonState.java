package tv.phantombot.scripts.handler.hexagon;

public enum HexagonState {
    DISCONNECTED("DISCONNECTED"),
    READY("READY"),
    GAME_ACTIVE("GAME_ACTIVE"),
    CONNECTING("CONNECTING"),
    ERROR("ERROR");

    public final String str;
    HexagonState(String s){
        str = s;
    }
}
