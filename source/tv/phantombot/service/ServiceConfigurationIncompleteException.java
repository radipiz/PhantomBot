package tv.phantombot.service;

public class ServiceConfigurationIncompleteException extends ServiceException {
    public ServiceConfigurationIncompleteException(String reason) {
        super(reason);
    }

    public ServiceConfigurationIncompleteException(String reason, Exception cause) {
        super(reason, cause);
    }
}
