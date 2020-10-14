package dev.jarcadia.vimes.model;

import dev.jarcadia.redao.proxy.Proxy;

public interface Artifact extends Proxy {
	
	String getApp();
	String getVersion();
	String getChecksum();
}
