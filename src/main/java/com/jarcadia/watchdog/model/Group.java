package com.jarcadia.watchdog.model;

import com.jarcadia.watchdog.States;
import com.jarcadia.watchdog.annontation.MonitoredValue;

import java.util.List;

public interface Group extends AppAssignable {

    List<Instance> getInstances();

    void setState(States.GroupState state);
}


