package dev.jarcadia.vimes.model;

import com.jarcadia.vimes.model.AlarmLevel;

public interface InstanceAlarm extends AppAssignable {
	
	public Instance getInstance();
	public AlarmLevel getLevel();
	public String getField();
}