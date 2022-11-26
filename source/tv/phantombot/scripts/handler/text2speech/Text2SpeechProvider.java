package tv.phantombot.scripts.handler.text2speech;

import tv.phantombot.common.Reloadable;

public interface Text2SpeechProvider extends Reloadable {
    byte[] synthesize(String text) throws Text2SpeechFailedException;

    byte[] synthesize(String text, TtsParams.Params params) throws Text2SpeechFailedException;

    default String getAudioMimeType() {
        return "audio/mp3";
    }

    String getProviderName();

    TtsParams.Params getParameters();
}
