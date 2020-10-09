package com.jarcadia.vimes.discovery;

import java.util.List;

public class FieldDef {

    private String name;
    private boolean valueSet;
    private Object value;

    private boolean published;
    private String displayName;
    private boolean writable;
    private FieldType type;

    private boolean monitored;
    private boolean increasingRange;
    private boolean decreasingRange;
    private List<String> panic;
    private List<String> critical;
    private List<String> warn;
    private List<String> attention;

    FieldDef() { }

    protected String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    protected Object getValue() {
        return value;
    }

    protected void setValue(Object value) {
        this.value = value;
    }

    public boolean isValueSet() {
        return valueSet;
    }

    public void setValueSet(boolean valueSet) {
        this.valueSet = valueSet;
    }

    protected boolean isPublished() {
        return published;
    }

    protected void setPublished(boolean published) {
        this.published = published;
    }

    protected String getDisplayName() {
        return displayName;
    }

    protected void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    protected boolean isWritable() {
        return writable;
    }

    protected void setWritable(boolean writable) {
        this.writable = writable;
    }

    protected FieldType getType() {
        return type;
    }

    protected void setType(FieldType type) {
        this.type = type;
    }

    protected boolean isMonitored() {
        return monitored;
    }

    protected void setMonitored(boolean monitored) {
        this.monitored = monitored;
    }

    protected boolean isIncreasingRange() {
        return increasingRange;
    }

    protected void setIncreasingRange(boolean increasingRange) {
        this.increasingRange = increasingRange;
    }

    protected boolean isDecreasingRange() {
        return decreasingRange;
    }

    protected void setDecreasingRange(boolean decreasingRange) {
        this.decreasingRange = decreasingRange;
    }

    protected List<String> getPanic() {
        return panic;
    }

    protected void setPanic(List<String> panic) {
        this.panic = panic;
    }

    protected List<String> getCritical() {
        return critical;
    }

    protected void setCritical(List<String> critical) {
        this.critical = critical;
    }

    protected List<String> getWarn() {
        return warn;
    }

    protected void setWarn(List<String> warn) {
        this.warn = warn;
    }

    protected List<String> getAttention() {
        return attention;
    }

    protected void setAttention(List<String> attention) {
        this.attention = attention;
    }
}
