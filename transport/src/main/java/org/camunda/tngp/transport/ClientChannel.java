package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

public interface ClientChannel extends TransportChannel
{

    public InetSocketAddress getRemoteAddress();

}
