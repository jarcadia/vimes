package dev.jarcadia.vimes;

import java.util.Map;

public class DiscoveredArtifact extends Discoverable {

    private final String app;
    private final String version;

    public DiscoveredArtifact(String app, String version) {
        this.app = app;
        this.version = version;
    }

    protected DiscoveredArtifact(String type, String version, Map<String, Object> properties) {
        super(properties);
        this.app = type;
        this.version = version;
    }

    protected String getApp() {
		return app;
	}

    protected String getVersion() {
		return version;
	}
}
