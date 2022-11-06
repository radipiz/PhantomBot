package tv.phantombot.scripts.handler.text2speech;

public class Text2SpeechFailedException extends Exception {

  public Text2SpeechFailedException(String reason) {
    super(reason);
  }
  public Text2SpeechFailedException(String reason, Exception cause) {
    super(reason, cause);
  }
}
