package com.jarcadia.watchdog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class MonitorDef {

    private final MonitorType type;
    private final List<String> panic;
    private final List<String> critical;
    private final List<String> warn;
    private final List<String> attention;

    @JsonCreator
    protected MonitorDef(@JsonProperty("type") MonitorType type, @JsonProperty("panic") List<String> panic,
            @JsonProperty("critical") List<String> critical, @JsonProperty("warn") List<String> warn,
            @JsonProperty("attention") List<String> attention) {
        this.type = type;
        this.panic = panic == null ? Collections.emptyList() : panic;
        this.critical = critical == null ? Collections.emptyList() : critical;
        this.warn = warn == null ? Collections.emptyList() : warn;
        this.attention = attention == null ? Collections.emptyList() : attention;
    }

    protected MonitorType getType() {
        return type;
    }

    protected List<String> getPanic() {
        return panic;
    }

    protected List<String> getCritical() {
        return critical;
    }

    protected List<String> getWarn() {
        return warn;
    }

    protected List<String> getAttention() {
        return attention;
    }

}
