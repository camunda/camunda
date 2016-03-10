package org.camunda.tngp.transport.impl.agent;

@FunctionalInterface
public interface SenderCmd
{
    void execute(Sender sender);
}
