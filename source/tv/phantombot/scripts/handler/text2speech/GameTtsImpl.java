package tv.phantombot.scripts.handler.text2speech;

import com.gmt2001.httpclient.HttpClient;
import com.gmt2001.httpclient.HttpClientResponse;

import java.net.URI;
import java.util.List;

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
    public static final String AUDIO_MIMETYPE = "audio/mp3";

    protected TtsParams.GameTtsParams parameters = new TtsParams.GameTtsParams();

    private final URI serviceUri;

    public GameTtsImpl() throws ServiceConfigurationIncompleteException {
        String key = Services.CONFIG_PREFIX_TEXT2SPEECH + CONFIG_SERVICE_URI;
        String value = CaselessProperties.instance().getProperty(key, "");
        if (value.isEmpty()) {
            throw new ServiceConfigurationIncompleteException("Required configuration key is missing or empty: " + key);
        }
        try {
            this.serviceUri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceConfigurationIncompleteException("Configuration key " + key + " contains invalid value: " + value, e);
        }

        String emotionId = CaselessProperties.instance().getProperty(Services.CONFIG_PREFIX_TEXT2SPEECH + CONFIG_EMOTION, "");
        String speakerId = CaselessProperties.instance().getProperty(Services.CONFIG_PREFIX_TEXT2SPEECH + CONFIG_EMOTION, "");
        String styleId = CaselessProperties.instance().getProperty(Services.CONFIG_PREFIX_TEXT2SPEECH + CONFIG_EMOTION, "");
        String speechSpeed = CaselessProperties.instance().getProperty(Services.CONFIG_PREFIX_TEXT2SPEECH + CONFIG_EMOTION, "");

        if(!emotionId.isEmpty()){
            parameters.Emotion_id(Integer.parseInt(emotionId));
        }
        if(!speakerId.isEmpty()){
            parameters.Speaker_id(Integer.parseInt(speakerId));
        }
        if(!styleId.isEmpty()){
            parameters.Style_id(Integer.parseInt(styleId));
        }
        if(!speechSpeed.isEmpty()){
            parameters.Speech_speed(Float.parseFloat(speechSpeed));
        }
    }

    @Override
    public byte[] synthesize(String text) throws Text2SpeechFailedException {
        return this.synthesize(text, getParameters());
    }

    @Override
    public byte[] synthesize(String text, TtsParams.Params params) throws Text2SpeechFailedException {
        JSONObject postParams = new JSONObject(params.getParams());
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
    public TtsParams.Params getParameters() {
        return parameters;
    }

    @Override
    public String getAudioMimeType(){
        return AUDIO_MIMETYPE;
    }

    @Override
    public String getProviderName(){
        return PROVIDER_NAME;
    }
}
