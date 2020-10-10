package dev.jarcadia.vimes.discovery;

import java.util.List;

public class FieldDefBuilder {

    private final FieldDef def;

    protected FieldDefBuilder(String name) {
        def = new FieldDef();
        def.setValueSet(false);
    }

    protected FieldDefBuilder(String name, Object value) {
        def = new FieldDef();
        def.setName(name);
        def.setValue(value);
        def.setValueSet(true);
    }

    public FieldDefBuilder publiclyReadableAs(String displayName, FieldType type) {
        def.setPublished(true);
        def.setDisplayName(displayName);
        def.setWritable(false);
        def.setType(type);
        return this;
    }

    public FieldDefBuilder publiclyWritableAs(String displayName, FieldType type) {
        def.setPublished(true);
        def.setDisplayName(displayName);
        def.setWritable(true);
        def.setType(type);
        return this;
    }

    public FieldDefBuilder monitorOnIncrease(Double attention, Double warn, Double critical, Double panic) {
        def.setMonitored(true);
        def.setIncreasingRange(true);
        this.setRangeValues(attention, warn, critical, panic);
        return this;
    }

    public FieldDefBuilder monitorOnDecrease(Double attention, Double warn, Double critical, Double panic) {
        def.setMonitored(true);
        def.setDecreasingRange(true);
        this.setRangeValues(attention, warn, critical, panic);
        return this;
    }

    private void setRangeValues(Double attention, Double warn, Double critical, Double panic) {
        def.setPanic(List.of(String.valueOf(panic)));
        def.setCritical(List.of(String.valueOf(critical)));
        def.setWarn(List.of(String.valueOf(warn)));
        def.setAttention(List.of(String.valueOf(attention)));
    }

    protected FieldDef getDef() {
        return def;
    }
}
