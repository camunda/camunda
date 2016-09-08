package org.camunda.tngp.dispatcher.impl;

@FunctionalInterface
public interface DispatcherConductorCommand
{
    void execute(DispatcherConductor conductor);
}
