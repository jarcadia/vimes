package com.jarcadia.watchdog.model;

import com.jarcadia.vimes.model.AlarmLevel;

public interface InstanceAlarm extends AppAssignable {
	
	public Instance getInstance();
	public AlarmLevel getLevel();
	public String getField();
}