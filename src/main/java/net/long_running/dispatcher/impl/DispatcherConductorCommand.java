package net.long_running.dispatcher.impl;

@FunctionalInterface
public interface DispatcherConductorCommand
{

    public void execute(DispatcherConductor conductor);
}
