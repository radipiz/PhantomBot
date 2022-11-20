package tv.phantombot.panel;

import static org.mockito.ArgumentMatchers.any;
import static tv.phantombot.service.Services.CONFIG_KEY_PROVIDER;
import static tv.phantombot.service.Services.CONFIG_PREFIX_TEXT2SPEECH;

import com.gmt2001.httpwsserver.WebSocketFrameHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import tv.phantombot.CaselessProperties;
import tv.phantombot.scripts.handler.text2speech.TtsParams;
import tv.phantombot.scripts.handler.text2speech.TtsParams.GameTtsParams;
import tv.phantombot.scripts.handler.text2speech.GameTtsImpl;
import tv.phantombot.service.Services;

import java.util.Base64;
import java.util.Map;

class WsAlertsPollsHandlerTest {

    WsAlertsPollsHandler handler;

    final String CONF_URI = "http://localhost:3000";
    final String CONF_SPEAKER = "1";
    final String CONF_STYLE = "7";
    final String CONF_EMOTION = "3";
    final String CONF_SPEED = "1.0";

    @BeforeEach
    void beforeEach() {
        this.handler = new WsAlertsPollsHandler("dontcare", "dontcare");
    }

    void MockConfiguration() {
        CaselessProperties props = CaselessProperties.instance();
        props.clear();
        props.putAll(Map.of(
                CONFIG_PREFIX_TEXT2SPEECH + '.' + CONFIG_KEY_PROVIDER, GameTtsImpl.PROVIDER_NAME,
                CONFIG_PREFIX_TEXT2SPEECH + '.' + GameTtsImpl.CONFIG_SERVICE_URI, CONF_URI,
                CONFIG_PREFIX_TEXT2SPEECH + '.' + GameTtsImpl.CONFIG_EMOTION, CONF_EMOTION,
                CONFIG_PREFIX_TEXT2SPEECH + '.' + GameTtsImpl.CONFIG_SPEAKER, CONF_SPEAKER,
                CONFIG_PREFIX_TEXT2SPEECH + '.' + GameTtsImpl.CONFIG_STYLE, CONF_STYLE,
                CONFIG_PREFIX_TEXT2SPEECH + '.' + GameTtsImpl.CONFIG_SPEECH_SPEED, CONF_SPEED
        ));
    }

    @Test
    void playTextToSpeech_withParams_successful() throws Exception {
        ArgumentCaptor<String> captor_uri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor_json = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor_text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TtsParams.Params> captor_params = ArgumentCaptor.forClass(TtsParams.Params.class);
        final String mimeType = "audio/mock";
        final String providerName = "MockTTS";

        final int emotionId = 3;
        final float speechSpeed = 1.1f;
        final int styleId = 3;
        final int speakerId = 230;

        final byte[] fakeAudio = new byte[]{71, 97, 109, 101, 84, 84, 83};
        final String fakeAudioBase64 = Base64.getEncoder().encodeToString(fakeAudio);

        // configure mocks
        MockConfiguration();
        GameTtsImpl mockGameTts = Mockito.mock(GameTtsImpl.class);
        Mockito.when(mockGameTts.synthesize(captor_text.capture(), captor_params.capture())).thenReturn(fakeAudio);
        Mockito.when(mockGameTts.getAudioMimeType()).thenReturn(mimeType);
        Mockito.when(mockGameTts.getProviderName()).thenReturn(providerName);


        TtsParams.GameTtsParams params = new GameTtsParams()
                .Emotion_id(emotionId).Speech_speed(speechSpeed).Style_id(styleId).Speaker_id(speakerId);
        try (MockedStatic<Services> servicesMock = Mockito.mockStatic(Services.class)) {
            servicesMock.when(Services::getText2Speech).thenReturn(mockGameTts);
            try (MockedStatic<WebSocketFrameHandler> wsFrameHandlerMock = Mockito.mockStatic(WebSocketFrameHandler.class)) {
                wsFrameHandlerMock.when(() -> WebSocketFrameHandler.broadcastWsFrame(captor_uri.capture(), any()))
                        .then((Answer<Void>) invocation -> null);
                wsFrameHandlerMock.when(() -> WebSocketFrameHandler.prepareTextWebSocketResponse(captor_json.capture()))
                        .thenReturn(new TextWebSocketFrame("dontcare"));
                this.handler.playTextToSpeech("test", params);

                // assert
                Assertions.assertEquals("/ws/alertspolls", captor_uri.getValue());
                Assertions.assertEquals(params, captor_params.getValue());

                JSONObject result = new JSONObject(captor_json.getValue());
                Map.ofEntries(
                        Map.entry("alerttype", "tts"),
                        Map.entry("engine", providerName),
                        Map.entry("text", "test"),
                        Map.entry("audio", fakeAudioBase64),
                        Map.entry("mimetype", mimeType)
                ).forEach((k, v) -> {
                    Assertions.assertTrue(result.has(k), "root object is missing key: " + k);
                    Assertions.assertEquals(v, result.get(k));
                });
                Assertions.assertTrue(result.has("params"), "root object is missing key: params");
                JSONObject resultParams = result.getJSONObject("params");
                Map.ofEntries(
                        Map.entry("emotion_id", String.valueOf(emotionId)),
                        Map.entry("speech_speed", String.valueOf(speechSpeed)),
                        Map.entry("style_id", String.valueOf(styleId)),
                        Map.entry("speaker_id", String.valueOf(speakerId))
                ).forEach((k, v) -> {
                    Assertions.assertTrue(resultParams.has(k), "'params' object is missing key: " + k);
                    Assertions.assertEquals(v, resultParams.get(k));
                });
            }
        }
    }
}
