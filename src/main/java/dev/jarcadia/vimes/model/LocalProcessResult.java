package dev.jarcadia.vimes.model;

import java.util.List;

public class LocalProcessResult {

    private final int exitCode;
    private final List<String> output;

    public LocalProcessResult(int exitCode, List<String> output) {
        this.exitCode = exitCode;
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public List<String> getOutput() {
        return output;
    }
}
