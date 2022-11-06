package tv.phantombot.panel;

import static org.mockito.ArgumentMatchers.any;
import static tv.phantombot.service.Services.CONFIG_KEY_PROVIDER;
import static tv.phantombot.service.Services.CONFIG_PREFIX_TEXT2SPEECH;

import com.gmt2001.httpwsserver.WebSocketFrameHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import tv.phantombot.CaselessProperties;
import tv.phantombot.panel.TtsParams.GameTtsParams;
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
    void playTextToSpeech_works() throws Exception {
        ArgumentCaptor<String> captor_uri = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor_json = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor_text = ArgumentCaptor.forClass(String.class);
        final byte[] fakeAudio = new byte[]{71, 97, 109, 101, 84, 84, 83};
        final String fakeAudioBase64 = Base64.getEncoder().encodeToString(fakeAudio);
        final String expectedJson = "{\"engine\":\"GameTTS\",\"text\":\"test\",\"audio\":\"" + fakeAudioBase64
                + "\",\"params\":{\"emotion_id\":1,\"speech_speed\":1,\"style_id\":1}}";
        MockConfiguration();
        GameTtsImpl mockGameTts = Mockito.mock(GameTtsImpl.class);
        Mockito.when(mockGameTts.synthesize(captor_text.capture())).thenReturn(fakeAudio);
        TtsParams.GameTtsParams params = new GameTtsParams()
                .Emotion_id(1).Speech_speed(1.0f).Style_id(1);
        try (MockedStatic<Services> servicesMock = Mockito.mockStatic(Services.class)) {
            servicesMock.when(Services::getText2Speech).thenReturn(mockGameTts);
            try (MockedStatic<WebSocketFrameHandler> wsFrameHandlerMock = Mockito.mockStatic(WebSocketFrameHandler.class)) {
                wsFrameHandlerMock.when(() -> WebSocketFrameHandler.broadcastWsFrame(captor_uri.capture(), any()))
                        .then((Answer<Void>) invocation -> null);
                wsFrameHandlerMock.when(() -> WebSocketFrameHandler.prepareTextWebSocketResponse(captor_json.capture()))
                        .thenReturn(new TextWebSocketFrame("dontcare"));
                this.handler.playTextToSpeech("test", params);

                Assertions.assertEquals(expectedJson, captor_json.getValue());
                Assertions.assertEquals("/ws/alertspolls", captor_uri.getValue());
            }
        }
    }
}
