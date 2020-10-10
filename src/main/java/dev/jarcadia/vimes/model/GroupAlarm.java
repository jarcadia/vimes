package dev.jarcadia.vimes.model;

import com.jarcadia.vimes.model.AlarmLevel;

public interface GroupAlarm extends AppAssignable {
	
	public Group getGroup();
	public AlarmLevel getLevel();
	public String getField();
}