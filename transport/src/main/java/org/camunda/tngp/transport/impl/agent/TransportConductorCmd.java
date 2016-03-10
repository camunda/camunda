package org.camunda.tngp.transport.impl.agent;

@FunctionalInterface
public interface TransportConductorCmd
{

    void execute(TransportConductor clientConductor);

}
