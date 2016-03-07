package net.long_running.transport;

import java.net.InetSocketAddress;

public interface ClientChannel extends BaseChannel
{

    public InetSocketAddress getRemoteAddress();

}
