package org.camunda.tngp.transport;

public interface ClientChannel extends TransportChannel
{
    SocketAddress getRemoteAddress();

}
