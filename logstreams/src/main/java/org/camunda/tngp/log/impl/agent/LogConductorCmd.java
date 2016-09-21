package org.camunda.tngp.log.impl.agent;

@FunctionalInterface
public interface LogConductorCmd
{
    void execute(LogConductor conductor);
}
