package tv.phantombot.scripts.handler.text2speech;

import com.gmt2001.httpclient.HttpClient;
import com.gmt2001.httpclient.HttpClientResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import tv.phantombot.CaselessProperties;
import tv.phantombot.service.ServiceConfigurationIncompleteException;
import tv.phantombot.service.Services;

public class GameTtsImpl implements Text2SpeechProvider {

    public static final String PROVIDER_NAME = "GameTTS";

    public static final String CONFIG_SERVICE_URI = "uri";
    public static final String CONFIG_EMOTION = "emotion";
    public static final String CONFIG_SPEAKER = "speaker";
    public static final String CONFIG_STYLE = "style";
    public static final String CONFIG_SPEECH_SPEED = "speech_speed";

    /**
     * Retrieved via /get_emotes
     * {
     * "Anger": 0,
     * "Calm": 1,
     * "Disgust": 2,
     * "Fear": 3,
     * "Happy": 4,
     * "Neutral": 5,
     * "Surprised": 6
     * }
     */
    public static final String PARAM_EMOTION_ID = "emotion_id";
    public static final String PARAM_SPEAKER_ID = "speaker_id";
    /**
     * Speed of the speech. Lower values are faster
     */
    public static final String PARAM_SPEECH_SPEED = "speech_speed";
    /**
     * Retrieved via /get_speech_styles
     * {
     * "Default": 0,
     * "Drunk": 90,
     * "Gnome": 206,
     * "Ogre": 37,
     * "Pirate": 28,
     * "Weird": 157
     * }
     */
    public static final String PARAM_STYLE_ID = "style_id";
    public static final String PARAM_TEXT = "text";

    public static final String PATH_SYNTHESIZE = "/synthesize";

    protected Map<String, Object> parameters = new HashMap<>();

    private final URI serviceUri;

    public GameTtsImpl() throws ServiceConfigurationIncompleteException {
        String key = Services.CONFIG_PREFIX_TEXT2SPEECH + '.' + CONFIG_SERVICE_URI;
        String value = CaselessProperties.instance().getProperty(key);
        if (value.isEmpty()) {
            throw new ServiceConfigurationIncompleteException("Required configuration key is missing or empty: " + key);
        }
        try {
            this.serviceUri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceConfigurationIncompleteException("Configuration key " + key + " contains invalid value: " + value, e);
        }

        Map.of(
                CONFIG_EMOTION, PARAM_EMOTION_ID,
                CONFIG_SPEAKER, PARAM_SPEAKER_ID,
                CONFIG_STYLE, PARAM_STYLE_ID
        ).forEach((configKey, paramKey) -> {
            String iKey = Services.CONFIG_PREFIX_TEXT2SPEECH + '.' + configKey;
            String iValue = CaselessProperties.instance().getProperty(iKey);
            if (!iValue.isEmpty()) {
                this.parameters.put(paramKey, Integer.parseInt(iValue));
            }
        });

        key = Services.CONFIG_PREFIX_TEXT2SPEECH + '.' + CONFIG_SPEECH_SPEED;
        value = CaselessProperties.instance().getProperty(key);
        if (!value.isEmpty()) {
            this.parameters.put(CONFIG_SPEECH_SPEED, Float.parseFloat(value));
        }

    }

    @Override
    public byte[] synthesize(String text) throws Text2SpeechFailedException {
        return this.synthesize(text, getParameters());
    }

    @Override
    public byte[] synthesize(String text, Map<String, Object> params) throws Text2SpeechFailedException {
        JSONObject postParams = new JSONObject();
        parameters.forEach(postParams::put);
        postParams.keySet().retainAll(List.of(PARAM_EMOTION_ID, PARAM_SPEAKER_ID, PARAM_STYLE_ID, PARAM_SPEECH_SPEED));
        postParams.put(PARAM_TEXT, text);
        HttpClientResponse response = HttpClient.post(URI.create(serviceUri + PATH_SYNTHESIZE), postParams);
        if (response.isSuccess()) {
            return response.rawResponseBody();
        } else {
            if (response.hasException()) {
                throw new Text2SpeechFailedException("Failed to synthesize speech", response.exception());
            }
            throw new Text2SpeechFailedException("Failed to synthesize speech: " + response.responseCode());
        }
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    public int getEmotionId() {
        return (int) parameters.get(PARAM_EMOTION_ID);
    }

    public void setEmotionId(int emotionId) {
        if (emotionId < 0 || emotionId > 7) {
            throw new IllegalArgumentException("Only values between 0 and 7 are possible");
        }
        parameters.put(PARAM_EMOTION_ID, emotionId);
    }

    public int getSpeakerId() {
        return (int) parameters.get(PARAM_SPEAKER_ID);
    }

    public void setSpeakerId(int speakerId) {
        if (speakerId < 1 || speakerId > 258) {
            throw new IllegalArgumentException("Only values between 1 and 258 are possible");
        }
        parameters.put(PARAM_SPEAKER_ID, speakerId);
    }

    public float getSpeechSpeed() {
        return (float) parameters.get(PARAM_SPEECH_SPEED);
    }

    public void setSpeechSpeed(float speechSpeed) {
        if (speechSpeed <= 0.0f || speechSpeed > 2) {
            throw new IllegalArgumentException("speech_speed must be within 0 and 2");
        }
        parameters.put(PARAM_SPEECH_SPEED, speechSpeed);
    }

    public int getStyleId() {
        return (int) parameters.get(PARAM_STYLE_ID);
    }

    public void setStyleId(int styleId) {
        if (styleId < 0 || styleId > 255) {
            throw new IllegalArgumentException("Only values between 0 and 255 are possible");
        }
        parameters.put(PARAM_STYLE_ID, styleId);
    }
}
