package com.jarcadia.watchdog;

import com.jarcadia.rcommando.Dao;

import java.util.List;
import java.util.Map;

public class DiscoveredGroup extends Discoverable {

    private final String id;
    private final List<Dao> instances;

    public DiscoveredGroup(String id, List<Dao> instances) {
        this.id = id;
        this.instances = instances;
    }
    
    protected DiscoveredGroup(String id, List<Dao> instances, Map<String, Object> properties) {
        super(properties);
        this.id = id;
        this.instances = instances;
    }
    
    protected String getId() {
		return id;
	}
    
    protected List<Dao> getInstances() {
		return instances;
	}
}
