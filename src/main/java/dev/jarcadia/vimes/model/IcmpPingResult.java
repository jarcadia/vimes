package dev.jarcadia.vimes.model;

public class IcmpPingResult {

    public static enum Status {
        SUCCESS,
        UNKNOWN_HOST,
        FAILED,
        TIMEOUT
    }

    private final Status status;
    private final long duration;

    public IcmpPingResult(Status status, long duration) {
        this.status = status;
        this.duration = duration;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public long getDuration() {
        return duration;
    }
}
