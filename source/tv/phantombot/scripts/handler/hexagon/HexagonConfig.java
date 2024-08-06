package tv.phantombot.scripts.handler.hexagon;

import org.apache.commons.lang3.StringUtils;
import tv.phantombot.CaselessProperties;

public class HexagonConfig {
    public static final String CONF_GAME_TIME = "hexagon.gametime";
    public static final String CONF_STOP_COMMAND = "hexagon.stopcommand";
    public static final String CONF_DEFAULT_LEVEL = "hexagon.default-level";
    public static final String CONF_MESSAGE_GAME_START_TEMPLATE = "hexagon.msg.game-start";
    public static final String CONF_MESSAGE_GAME_END_TEMPLATE = "hexagon.msg.game-end";
    public static final String CONF_MESSAGE_TIME_ANNOUNCE_TEMPLATE = "hexagon.msg.time-announce";
    public static final String CONF_MESSAGE_TIME_ANNOUNCE_SHORT_TEMPLATE = "hexagon.msg.time-announce-short";

    public static final String CONF_HOST = "hexagon.host";
    public static final String CONF_PORT = "hexagon.port";
    public static final String CONF_ENABLED = "hexagon.enabled";

    public static final String DEFAULT_STOP_COMMAND = "!stop";
    public static final int DEFAULT_GAME_TIME = 60;
    public static final boolean DEFAULT_ENABLED = false;
    private static final String DEFAULT_MESSAGE_START_TEMPLATE = "Das Hexagon! @{playername} spielt und kann mit {stopcommand} das Hexagon innerhalb von {gametime} Sekunden anhalten!";
    private static final String DEFAULT_MESSAGE_END_TEMPLATE = "Aus! Das Spiel ist vorbei! Das Hexagon hat entschieden!";
    private static final String DEFAULT_MESSAGE_TIME_ANNOUNCE = "Noch {gametime} Sekunden!";
    private static final String DEFAULT_MESSAGE_TIME_ANNOUNCE_SHORT = "{gametime}!";

    public static final int DEFAULT_LEVEL = 2;

    public void configurationCheck() {
        final String MSG_MISSING_KEY = "config is lacking entry: ";
        final String MSG_INVALID_VALUE = "invalid config value for entry: ";
        if (!CaselessProperties.instance().containsKey(CONF_PORT)) {
            throw new IllegalArgumentException(MSG_MISSING_KEY + CONF_PORT);
        }
        if (StringUtils.isEmpty(getHost())) {
            throw new IllegalArgumentException(MSG_MISSING_KEY + CONF_HOST);
        }
        int port = CaselessProperties.instance().getPropertyAsInt(CONF_PORT, 0);
        if (0 >= port || port >= 65535) {
            throw new IllegalArgumentException(MSG_INVALID_VALUE + CONF_PORT);
        }
    }

    public int getGameTime() {
        return CaselessProperties.instance().getPropertyAsInt(CONF_GAME_TIME, DEFAULT_GAME_TIME);
    }

    public String getStopCommand() {
        return CaselessProperties.instance().getProperty(CONF_STOP_COMMAND, DEFAULT_STOP_COMMAND);
    }

    public int getDefaultLevel() {
        int configuredValue = CaselessProperties.instance().getPropertyAsInt(CONF_DEFAULT_LEVEL, DEFAULT_LEVEL);
        int clampedValue = Math.max(0, Math.min(10, configuredValue));
        if (configuredValue != clampedValue) {
            com.gmt2001.Console.warn.println(String.format("Value for %s got clamped to %s (was %s)", CONF_DEFAULT_LEVEL, clampedValue, configuredValue));
        }
        return clampedValue;
    }

    public String getHost() {
        return CaselessProperties.instance().getProperty(CONF_HOST);
    }

    public int getPort() {
        return CaselessProperties.instance().getPropertyAsInt(CONF_PORT);
    }

    public boolean isEnabled() {
        return CaselessProperties.instance().getPropertyAsBoolean(CONF_ENABLED, DEFAULT_ENABLED);
    }

    public String getGameStartMessage(String playerName) {
        return getGameStartMessage(playerName, getGameTime());
    }

    public String getGameStartMessage(String playerName, int gameTime) {
        final String[] keyNames = new String[]{"{playername}", "{stopcommand}", "{gametime}"};
        final String[] replacements = new String[]{playerName, getStopCommand(), String.valueOf(gameTime)};

        String template = CaselessProperties.instance().getProperty(CONF_MESSAGE_GAME_START_TEMPLATE, DEFAULT_MESSAGE_START_TEMPLATE);
        return StringUtils.replaceEach(template, keyNames, replacements);
    }

    public String getGameEndMessage() {
        return CaselessProperties.instance().getProperty(CONF_MESSAGE_GAME_END_TEMPLATE, DEFAULT_MESSAGE_END_TEMPLATE);
    }

    public String getTimeAnnounceMessage(int gameTimeLeft) {
        return StringUtils.replace(
                CaselessProperties.instance().getProperty(CONF_MESSAGE_TIME_ANNOUNCE_TEMPLATE, DEFAULT_MESSAGE_TIME_ANNOUNCE),
                "{gametime}", String.valueOf(gameTimeLeft));
    }

    public String getTimeShortAnnounceMessage(int gameTimeLeft) {
        return StringUtils.replace(
                CaselessProperties.instance().getProperty(CONF_MESSAGE_TIME_ANNOUNCE_SHORT_TEMPLATE, DEFAULT_MESSAGE_TIME_ANNOUNCE_SHORT),
                "{gametime}", String.valueOf(gameTimeLeft));
    }
}
