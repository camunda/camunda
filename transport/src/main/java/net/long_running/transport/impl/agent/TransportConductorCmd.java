package net.long_running.transport.impl.agent;

@FunctionalInterface
public interface TransportConductorCmd
{

    void execute(TransportConductor clientConductor);

}
