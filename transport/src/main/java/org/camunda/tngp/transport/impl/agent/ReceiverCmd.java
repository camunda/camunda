package org.camunda.tngp.transport.impl.agent;

@FunctionalInterface
public interface ReceiverCmd
{
    void execute(Receiver receiver);
}
