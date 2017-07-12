package io.zeebe.transport;

import org.agrona.DirectBuffer;

public interface ClientMessageHandler
{
    boolean onMessage(ClientOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length);
}
