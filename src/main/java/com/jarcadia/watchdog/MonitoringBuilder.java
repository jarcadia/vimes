package com.jarcadia.watchdog;

import java.math.BigDecimal;

public class MonitoringBuilder {

    private final String fieldName;
    private AscendingRangeBuilder ascendingRangeBuilder;

    public MonitoringBuilder(String fieldName) {
        this.fieldName = fieldName;
    }

    public AscendingRangeBuilder byAscendingRange() {
        ascendingRangeBuilder = new AscendingRangeBuilder();
        return ascendingRangeBuilder;
    }

    public static class AscendingRangeBuilder {

        private BigDecimal panic;
        private BigDecimal critical;
        private BigDecimal warn;
        private BigDecimal attention;

        public AscendingRangeBuilder panicAt(double value) {
            panic = new BigDecimal(value);
            return this;
        }
    }
}


