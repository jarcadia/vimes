package dev.jarcadia.vimes.model;

import dev.jarcadia.vimes.States;

import java.util.List;

public interface Group extends AppAssignable {

    List<Instance> getInstances();

    void setState(States.GroupState state);
}


