package tv.phantombot.service;

public class ServiceNotConfiguredException extends ServiceException {
    public ServiceNotConfiguredException(String reason) {
        super(reason);
    }
}
