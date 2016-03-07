package net.long_running.transport;

import java.net.InetSocketAddress;

public interface ServerSocketBinding
{

    InetSocketAddress getBindAddress();

    void close();

}