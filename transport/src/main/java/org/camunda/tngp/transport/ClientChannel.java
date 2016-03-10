package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

public interface ClientChannel extends BaseChannel
{

    public InetSocketAddress getRemoteAddress();

}
