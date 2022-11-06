package tv.phantombot.scripts.handler.text2speech;

import java.util.Map;

public interface Text2SpeechProvider {
  byte[] synthesize(String text) throws Text2SpeechFailedException;
  byte[] synthesize(String text, Map<String, Object> params) throws Text2SpeechFailedException;

  Map<String, Object> getParameters();
}
