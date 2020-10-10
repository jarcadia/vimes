package dev.jarcadia.vimes.exception;

public class WatchdogException extends RuntimeException {

    public WatchdogException(String message)
    {
        super(message);
    }

    public WatchdogException(Throwable cause)
    {
        super(cause);
    }

    public WatchdogException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
