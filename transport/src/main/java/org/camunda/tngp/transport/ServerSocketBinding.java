package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

public interface ServerSocketBinding
{

    InetSocketAddress getBindAddress();

    void close();

}