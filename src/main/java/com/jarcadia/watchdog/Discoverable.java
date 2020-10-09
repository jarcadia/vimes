package com.jarcadia.watchdog;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

abstract class Discoverable {

    private final Map<String, Object> properties;

    public Discoverable() {
        properties = new HashMap<>();
    }

    public void add(String fieldName, Object value) {
        properties.put(fieldName, value);
    }

    /**
     * Internal use constructor, used when deserializing task response
     */
    public Discoverable(Map<String, Object> properties) {
        this.properties = properties;
    }

    protected Map<String, Object> getProperties() {
        return properties;
    }
}
