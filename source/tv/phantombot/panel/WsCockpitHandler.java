package tv.phantombot.panel;

import com.gmt2001.httpwsserver.WebSocketFrameHandler;
import com.gmt2001.httpwsserver.WsFrameHandler;
import com.gmt2001.httpwsserver.auth.WsAuthenticationHandler;
import com.gmt2001.httpwsserver.auth.WsSharedRWTokenAuthenticationHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.json.JSONStringer;
import tv.phantombot.CaselessProperties;
import tv.phantombot.PhantomBot;
import tv.phantombot.event.Listener;
import tv.phantombot.event.irc.message.IrcChannelMessageEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class WsCockpitHandler implements WsFrameHandler, Listener {

    public static final String WS_PATH = "/ws/cockpit";

    public static final String KEY_DURATION = "duration";
    public static final String KEY_EMOTE_ANIMATION_NAME = "animationName";
    public static final String KEY_EMOTE_AMOUNT = "amount";
    public static final String KEY_EMOTE_ID = "emoteId";
    public static final String KEY_EMOTE_PROVIDER = "provider";
    public static final String KEY_ERROR = "error";
    public static final String KEY_FILENAME = "filename";
    public static final String KEY_EMOTE_IGNORE_SLEEP = "ignoreSleep";
    public static final String KEY_KEY = "key";
    public static final String KEY_MACRO_DATA = "macroData";
    public static final String KEY_REQUEST_TYPE = "requestType";
    public static final String KEY_REQUEST_ID = "requestId";
    public static final String KEY_RESPONSE = "response";
    public static final String KEY_RESULT = "result";
    public static final String KEY_STATUS = "status";
    public static final String KEY_TABLE = "table";
    public static final String KEY_TYPE = "type";
    public static final String KEY_VALUE = "value";

    public static final String REQUEST_GET_DB_VALUE = "getDBValue";
    public static final String REQUEST_GET_DB_VALUES = "getDBValues";
    public static final String REQUEST_SET_DB_VALUE = "setDBValue";
    public static final String REQUEST_CLIENT_PRESENT = "isClientPresent";
    public static final String REQUEST_PLAY_SOUND = "playSound";
    public static final String REQUEST_PLAY_CLIP = "playClip";
    public static final String REQUEST_PLAY_MACRO = "playMacro";
    public static final String REQUEST_STOP_MEDIA = "stopMedia";
    public static final String REQUEST_TRIGGER_EMOTE = "triggerEmote";

    public static final String REQUEST_GET_AUDIO_FILES = "getAudioFiles";
    public static final String REQUEST_GET_CLIP_FILES = "getClipFiles";

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    public static final String REQUEST_ID_SYSTEM_MESSAGE = "SYSTEM";
    public static final String REQUEST_ID_IRC_CHANNEL_MESSAGE = "ircChannelMessage";

    protected static final String[] FILE_EXTENSIONS_AUDIO = {"mp3", "ogg", "aac"};
    protected static final String[] FILE_EXTENSIONS_CLIPS = {"mp4", "gif", "webm"};
    public static final String STRING_JSON_IS_MISSING_KEY = "Json is missing key ";

    private final WsAuthenticationHandler authHandler;
    private final WsAlertsPollsHandler alertsHandler;

    public WsCockpitHandler(String panelAuthRO, String panelAuth, WsAlertsPollsHandler alertsPollsHandler) {
        authHandler = new WsSharedRWTokenAuthenticationHandler(panelAuthRO, panelAuth, 10);
        alertsHandler = alertsPollsHandler;
    }

    @Override
    public WsFrameHandler register() {
        WebSocketFrameHandler.registerWsHandler(WS_PATH, this);
        return this;
    }

    @Override
    public WsAuthenticationHandler getAuthHandler() {
        return authHandler;
    }

    @Override
    public void handleFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame tframe = (TextWebSocketFrame) frame;

            JSONStringer response = new JSONStringer();
            response.object();
            JSONObject json = null;
            //noinspection ProhibitedExceptionCaught
            try {
                json = new JSONObject(tframe.text());

                if ("true".equalsIgnoreCase(
                    CaselessProperties.instance().getProperty("wsdebug", "false")
                )) {
                    com.gmt2001.Console.debug.println(json.toString());
                }

                if (!ctx.channel().attr(WsSharedRWTokenAuthenticationHandler.ATTR_IS_READ_ONLY).get()) {
                    handleRestrictedCommands(json, response);
                }
            } catch (NullPointerException ex) {
                // This can happen during startup, when PhantomBot.java is still in the object's construction
                // It'll construct this websocket handler and if a client has a running request it'll land here
                // while the instance construction has not been finished and written to the instance holder.
                // Checking if PhantomBot.instance() is null might have changed in the meantime, so a second check
                // is done to check if the exception was thrown in this method and not any handler which is a different
                // problem than the startup phase.
                if (PhantomBot.instance() == null
                        || Thread.currentThread().getStackTrace()[1].getMethodName().equals(ex.getStackTrace()[0].getMethodName())) {
                    response.key(KEY_REQUEST_ID).value(REQUEST_ID_SYSTEM_MESSAGE)
                            .key(KEY_RESPONSE).object()
                            .key(KEY_STATUS).value(STATUS_ERROR)
                            .key(KEY_ERROR).value("Application is still starting and cannot handle requests yet.")
                            .endObject();
                } else {
                    StackTraceElement traceElement = ex.getStackTrace()[0];
                    String errorMessage = String.format("%s in %s.%s:%s",
                            ex.getClass().getCanonicalName(),
                            traceElement.getClassName(),
                            traceElement.getMethodName(),
                            traceElement.getLineNumber()
                    );
                    response.key(KEY_REQUEST_ID).value(REQUEST_ID_SYSTEM_MESSAGE)
                            .key(KEY_RESPONSE).object()
                            .key(KEY_STATUS).value(STATUS_ERROR)
                            .key(KEY_ERROR).value(errorMessage)
                            .endObject();
                    com.gmt2001.Console.err.println(errorMessage);
                }
            } catch (Exception ex) {
                response = new JSONStringer();
                response.object()
                        .key(KEY_RESPONSE).object()
                        .key(KEY_STATUS).value(STATUS_ERROR)
                        .key(KEY_ERROR).value(String.format("%s: %s", ex.getClass().getSimpleName(), ex.getMessage()))
                        .endObject();
            } finally {
                if (json != null && json.has(KEY_REQUEST_ID)) {
                    response.key(KEY_REQUEST_ID).value(json.getString(KEY_REQUEST_ID));
                }
                response.endObject();
            }
            WebSocketFrameHandler.sendWsFrame(ctx, frame, WebSocketFrameHandler.prepareTextWebSocketResponse(response.toString()));
        }
    }

    private void handleRestrictedCommands(JSONObject json, JSONStringer response) throws InvalidRequestException {
        if (!json.has(KEY_REQUEST_TYPE)) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_REQUEST_TYPE);
        }
        if (!json.has(KEY_REQUEST_ID)) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_REQUEST_ID);
        }
        response.key(KEY_RESPONSE).object();
        try {
            final String requestType = json.getString(KEY_REQUEST_TYPE);
            switch (requestType) {
                case REQUEST_GET_DB_VALUE:
                    handleGetDbValue(json, response);
                    break;
                case REQUEST_SET_DB_VALUE:
                    handleSetDbValue(json, response);
                    break;
                case REQUEST_GET_DB_VALUES:
                    handleGetDbValues(json, response);
                    break;
                case REQUEST_CLIENT_PRESENT:
                    handleIsClientPresent(response);
                    break;
                case REQUEST_PLAY_SOUND:
                    handlePlaySound(json, response);
                    break;
                case REQUEST_PLAY_CLIP:
                    handlePlayClip(json, response);
                    break;
                case REQUEST_PLAY_MACRO:
                    handlePlayMacro(json, response);
                    break;
                case REQUEST_TRIGGER_EMOTE:
                    handleTriggerEmote(json, response);
                    break;
                case REQUEST_STOP_MEDIA:
                    handleStopMedia(json, response);
                    break;
                case REQUEST_GET_AUDIO_FILES:
                    handleGetAudioFiles(response);
                    break;
                case REQUEST_GET_CLIP_FILES:
                    handleGetClipFiles(response);
                    break;
                default:
                    throw new InvalidRequestException(String.format("Unknown command '%s'", requestType));
            }
        } finally {
            response.endObject();
        }
    }

    private static void handleSetDbValue(JSONObject json, final JSONStringer response) {
        final String table = json.getString(KEY_TABLE);
        final String key = json.getString(KEY_KEY);
        final String value = json.getString(KEY_VALUE);

        PhantomBot.instance().getDataStore().set(table, key, value);
        response.key(KEY_STATUS).value(STATUS_OK);
    }

    private static void handleGetDbValue(JSONObject json, final JSONStringer response) {
        String table = json.getString(KEY_TABLE);
        String key = json.getString(KEY_KEY);

        String value = PhantomBot.instance().getDataStore().GetString(table, "", key);

        response.key(KEY_STATUS).value(STATUS_OK)
                .key(KEY_TABLE).value(table)
                .key(KEY_KEY).value(key)
                .key(KEY_VALUE).value(value);
    }

    private static void handleGetDbValues(JSONObject json, final JSONStringer response) {
        String table = json.getString(KEY_TABLE);

        response.key("rows").array();
        String[] dbKeys = PhantomBot.instance().getDataStore().GetKeyList(table, "");
        for (String dbKey : dbKeys) {
            final String value = PhantomBot.instance().getDataStore().GetString(table, "", dbKey);
            response.object()
                    .key(KEY_KEY).value(dbKey)
                    .key(KEY_VALUE).value(value)
                    .endObject();
        }
        response.endArray()
                .key(KEY_STATUS).value(STATUS_OK);
    }

    private Collection<String> listFilePaths(String[] sourcePaths, String... extensions) {
        Collection<String> result = new ArrayList<>();
        for (String sourcePath : sourcePaths) {
            int startIndex = sourcePath.length() + 1;
            Collection<File> audioFilesInPath = FileUtils.listFiles(new File(sourcePath), extensions, true);
            result.addAll(audioFilesInPath.stream()
                    .map(file -> file.getPath().substring(startIndex))
                    .collect(Collectors.toList()));
        }
        return result;
    }

    private void handleGetAudioFiles(final JSONStringer response) {
        String[] sourcePaths = {"config/audio-hooks", "config/gif-alerts"};
        Collection<String> audioFiles = listFilePaths(sourcePaths, FILE_EXTENSIONS_AUDIO);
        response.key("files").value(audioFiles)
                .key(KEY_STATUS).value(STATUS_OK);
    }

    private void handleGetClipFiles(final JSONStringer response) {
        String[] sourcePaths = {"config/clips"};
        Collection<String> clipFiles = listFilePaths(sourcePaths, FILE_EXTENSIONS_CLIPS);
        response.key("files").value(clipFiles)
                .key(KEY_STATUS).value(STATUS_OK);
    }

    private static void handleIsClientPresent(final JSONStringer response) {
        response.key(KEY_RESULT).value(WebSocketFrameHandler.getWsSessions("/ws/alertspolls").size());
    }

    private void handlePlaySound(JSONObject json, final JSONStringer response) throws InvalidRequestException {
        final String filename = json.getString(KEY_FILENAME);
        if (filename == null) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_FILENAME);
        }
        alertsHandler.triggerAudioPanel(filename, true);
        response.key(KEY_STATUS).value(STATUS_OK);
    }

    private void handlePlayClip(JSONObject json, final JSONStringer response) throws InvalidRequestException {
        final String filename = json.getString(KEY_FILENAME);
        if (filename == null) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_FILENAME);
        }
        // Todo: Add options here and manipulate the filename
        alertsHandler.playVideo(filename, -1, true);
        response.key(KEY_STATUS).value(STATUS_OK);
    }

    private void handlePlayMacro(JSONObject json, final JSONStringer response) throws InvalidRequestException {
        final String macroJson = json.getString(KEY_MACRO_DATA);
        if (macroJson == null) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_MACRO_DATA);
        }
        alertsHandler.sendMacro(macroJson);
        response.key(KEY_STATUS).value(STATUS_OK);
    }

    private void handleStopMedia(JSONObject json, final JSONStringer response) throws InvalidRequestException {
        final String mediaType = json.getString(KEY_TYPE);
        if (mediaType == null) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_TYPE);
        }
        alertsHandler.stopMedia(mediaType);
        response.key(KEY_STATUS).value(STATUS_OK);
    }

    private void handleTriggerEmote(JSONObject json, final JSONStringer response) throws InvalidRequestException {
        final String emoteId = json.getString(KEY_EMOTE_ID);
        final String emoteProvider = json.getString(KEY_EMOTE_PROVIDER);
        boolean ignoreSleep = json.has(KEY_EMOTE_IGNORE_SLEEP) && json.getBoolean(KEY_EMOTE_IGNORE_SLEEP);

        if (emoteId == null) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_EMOTE_ID);
        }
        if (emoteProvider == null) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_EMOTE_PROVIDER);
        }
        if (!json.has(KEY_EMOTE_AMOUNT)) {
            throw new InvalidRequestException(STRING_JSON_IS_MISSING_KEY + KEY_EMOTE_AMOUNT);
        }
        String animationName = null;
        if (json.has(KEY_EMOTE_ANIMATION_NAME)) {
            animationName = json.getString(KEY_EMOTE_ANIMATION_NAME);
        }
        int duration = -1;
        if (json.has(KEY_DURATION)) {
            duration = json.getInt(KEY_DURATION);
        }
        final int amount = json.getInt(KEY_EMOTE_AMOUNT);

        if (animationName != null) {
            alertsHandler.triggerEmoteAnimation(emoteId, amount, emoteProvider, animationName, duration, ignoreSleep);
        } else {
            alertsHandler.triggerEmote(emoteId, amount, emoteProvider, ignoreSleep);
        }
        response.key(KEY_STATUS).value(STATUS_OK);
    }

    @Handler
    private static void onIrcChannelMessage(IrcChannelMessageEvent event) {
        JSONStringer json = new JSONStringer();
        json.object()
                .key(KEY_REQUEST_ID).value(REQUEST_ID_IRC_CHANNEL_MESSAGE)
                .key("message").value(event.getMessage())
                .key("tags").value(event.getTags())
                .key("sender").value(event.getSender())
                .key("id").value(event.getTags().getOrDefault("id", java.util.UUID.randomUUID().toString()))
                .key("timestamp").value(Long.parseLong(event.getTags().getOrDefault("tmi-sent-ts", "1616899474000")))
                .endObject();
        WebSocketFrameHandler.broadcastWsFrame(WS_PATH, WebSocketFrameHandler.prepareTextWebSocketResponse(json.toString()));
    }

    public static class InvalidRequestException extends Exception {

        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
