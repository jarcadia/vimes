package dev.jarcadia.vimes.model;

import java.util.Optional;

import dev.jarcadia.vimes.DeployState;
import dev.jarcadia.vimes.States.InstanceState;

public interface Instance extends AppAssignable {
	
	public String getHost();
	public Optional<Group> getGroup();
	public InstanceState getState();
	public Optional<DeployState> getDeploymentState();
	
	public void setState(InstanceState state);
	
}