package dev.jarcadia.vimes.model;

import dev.jarcadia.redao.proxy.Proxy;
import dev.jarcadia.vimes.States.DistributionState;

public interface Distribution extends Proxy {
	
	public String getApp();
	public String getHost();
	public Artifact getArtifact();
    public DistributionState getState();

	public void setState(DistributionState state);
	public void setProgress(double progress);

}
