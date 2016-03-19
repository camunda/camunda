package org.camunda.tngp.log.conductor;

@FunctionalInterface
public interface LogConductorCmd
{
    void execute(LogConductor conductor);
}
