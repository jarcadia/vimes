package dev.jarcadia.vimes.model;

import com.jarcadia.rcommando.proxy.Proxy;

public interface Artifact extends Proxy {
	
	String getApp();
	String getVersion();
	String getChecksum();
}
