package dev.jarcadia.vimes.model;

public interface GroupAlarm extends AppAssignable {
	
	public Group getGroup();
	public AlarmLevel getLevel();
	public String getField();
}