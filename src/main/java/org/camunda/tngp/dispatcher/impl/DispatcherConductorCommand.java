package org.camunda.tngp.dispatcher.impl;

@FunctionalInterface
public interface DispatcherConductorCommand
{

    public void execute(DispatcherConductor conductor);
}
