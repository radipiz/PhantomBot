package tv.phantombot.common;

public interface Reloadable {
    /**
     * Requests an object to reload its configuration or other properties.
     */
    void reload() throws BotException;
}
