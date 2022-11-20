package tv.phantombot.service;

import tv.phantombot.CaselessProperties;
import tv.phantombot.scripts.handler.text2speech.GameTtsImpl;
import tv.phantombot.scripts.handler.text2speech.Text2SpeechProvider;

public class Services {
    public static final String CONFIG_PREFIX_TEXT2SPEECH = "services.text2speech.";
    public static final String CONFIG_KEY_PROVIDER = "provider";
    private static Text2SpeechProvider tts;

    private static Text2SpeechProvider createText2Speech() throws ServiceNotConfiguredException, ServiceConfigurationIncompleteException {
        String provider = CaselessProperties.instance().getProperty(CONFIG_PREFIX_TEXT2SPEECH + CONFIG_KEY_PROVIDER, "");
        Text2SpeechProvider ttsProvider;
        com.gmt2001.Console.debug.println(String.format("Trying to configure text2speech provider. '%s'", provider));
        switch(provider){
            case GameTtsImpl.PROVIDER_NAME:
                ttsProvider = new GameTtsImpl();
                break;
            default:
                throw new ServiceNotConfiguredException("Text2Speech service is not configured. Please configure add services.text2speech.provider to config");
        }
        return ttsProvider;
    }

    public static Text2SpeechProvider getText2Speech() throws ServiceNotConfiguredException, ServiceConfigurationIncompleteException {
        if(tts == null){
            tts = createText2Speech();
        }
        return tts;
    }

}
