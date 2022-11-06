package tv.phantombot.service;

public class ServiceException extends Exception {
    public ServiceException() {
    }

    public ServiceException(String reason) {
        super(reason);
    }

    public ServiceException(Exception cause) {
        super(cause);
    }

    public ServiceException(String reason, Exception cause) {
        super(reason, cause);
    }

}
