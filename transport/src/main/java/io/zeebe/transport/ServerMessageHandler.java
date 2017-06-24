package io.zeebe.transport;

import org.agrona.DirectBuffer;

@FunctionalInterface
public interface ServerMessageHandler
{
    boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length);
}
