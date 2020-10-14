package dev.jarcadia.vimes.model;

public interface InstanceAlarm extends AppAssignable {
	
	public Instance getInstance();
	public AlarmLevel getLevel();
	public String getField();
}