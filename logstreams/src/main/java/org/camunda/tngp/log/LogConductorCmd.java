package org.camunda.tngp.log;

@FunctionalInterface
public interface LogConductorCmd
{
    void execute(LogConductor conductor);
}
