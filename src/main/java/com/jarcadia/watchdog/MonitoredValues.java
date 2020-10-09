package com.jarcadia.watchdog;

import java.math.BigDecimal;
import java.util.List;

public class MonitoredValues {

    private List<String> panic;
    private List<String> critical;
    private List<String> warn;
    private List<String> attention;

    public static MonitoredValues create() {
        return new MonitoredValues();
    }

    private MonitoredValues() {
    }

    public <T extends Enum> MonitoredValues panicWhen(T... values) {
        for(T value : values) {
            panic.add(value.name());
        }
        return this;
    }
}
