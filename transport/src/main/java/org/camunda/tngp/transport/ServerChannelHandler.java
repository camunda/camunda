package org.camunda.tngp.transport;

@FunctionalInterface
public interface ServerChannelHandler
{
    void onChannelAccepted(ServerChannel serverChannel);
}
