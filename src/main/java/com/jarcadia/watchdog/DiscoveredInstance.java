package com.jarcadia.watchdog;

import java.util.Map;

public class DiscoveredInstance extends Discoverable {

    private final String app;
    private final String id;

    public DiscoveredInstance(String app, String id) {
        super();
        this.id = id;
        this.app = app;
    }

    /**
     * Internal use constructor, used when deserializing task response
     */
    protected DiscoveredInstance(String app, String id, Map<String, Object> properties) {
        super(properties);
        this.id = id;
        this.app = app;
    }

    protected String getApp() {
		return app;
	}

    protected String getId() {
		return id;
	}
}
