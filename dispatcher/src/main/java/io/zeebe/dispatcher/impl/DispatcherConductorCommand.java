package io.zeebe.dispatcher.impl;

@FunctionalInterface
public interface DispatcherConductorCommand
{
    void execute(DispatcherConductor conductor);
}
