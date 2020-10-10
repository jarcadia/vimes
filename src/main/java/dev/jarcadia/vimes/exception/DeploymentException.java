package dev.jarcadia.vimes.exception;

public class DeploymentException extends RuntimeException {

    public DeploymentException(String message)
    {
        super(message);
    }

    public DeploymentException(Throwable cause)
    {
        super(cause);
    }

    public DeploymentException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
