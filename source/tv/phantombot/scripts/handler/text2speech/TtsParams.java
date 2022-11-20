package tv.phantombot.scripts.handler.text2speech;

import java.util.Map;

public class TtsParams {

    public interface Params {
        Map<String, String> getParams();
    }
    public static class GameTtsParams implements Params {
        private int emotionId = 5;
        private int style_id = 0;
        private float speech_speed = 1.1f;
        private int speaker_id = 1;


        public int getEmotion_id() {
            return emotionId;
        }

        public GameTtsParams Emotion_id(int emotion_id) {
            if (emotion_id < 0 || emotion_id > 7) {
                throw new IllegalArgumentException("Only values between 0 and 7 are possible");
            }
            this.emotionId = emotion_id;
            return this;
        }

        public int getStyle_id() {
            return style_id;
        }

        public GameTtsParams Style_id(int style_id) {
            if (style_id < 0 || style_id > 255) {
                throw new IllegalArgumentException("Only values between 0 and 255 are possible");
            }
            this.style_id = style_id;
            return this;
        }

        public float getSpeech_speed() {
            return speech_speed;
        }

        public GameTtsParams Speech_speed(float speech_speed) {
            if (speech_speed <= 0.0f || speech_speed > 2) {
                throw new IllegalArgumentException("speech_speed must be within 0 and 2");
            }
            this.speech_speed = speech_speed;
            return this;
        }

        public int getSpeaker_id() {
            return speaker_id;
        }

        public GameTtsParams Speaker_id(int speaker_id) {
            if (speaker_id < 0 || speaker_id > 258) {
                throw new IllegalArgumentException("Only values between 0 and 258 are possible");
            }
            this.speaker_id = speaker_id;
            return this;
        }

        @Override
        public Map<String, String> getParams() {
            return Map.ofEntries(
                    Map.entry(GameTtsImpl.PARAM_EMOTION_ID, String.valueOf(emotionId)),
                    Map.entry(GameTtsImpl.PARAM_SPEAKER_ID, String.valueOf(speaker_id)),
                    Map.entry(GameTtsImpl.PARAM_SPEECH_SPEED, String.valueOf(speech_speed)),
                    Map.entry(GameTtsImpl.PARAM_STYLE_ID, String.valueOf(style_id))
            );
        }
    }
}
