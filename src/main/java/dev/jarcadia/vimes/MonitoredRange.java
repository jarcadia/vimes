package dev.jarcadia.vimes;

import java.math.BigDecimal;

public class MonitoredRange {

    private final boolean ascending;
    private final BigDecimal panic;
    private final BigDecimal critical;
    private final BigDecimal warn;
    private final BigDecimal attention;

    protected MonitoredRange(boolean ascending, Double panic, Double critical, Double warn, Double attention) {
        this.ascending = ascending;
        this.panic = new BigDecimal(panic);
        this.critical = new BigDecimal(critical);
        this.warn = new BigDecimal(warn);
        this.attention = new BigDecimal(attention);
    }
}
