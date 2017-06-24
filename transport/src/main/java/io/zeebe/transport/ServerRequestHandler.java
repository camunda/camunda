package io.zeebe.transport;

import org.agrona.DirectBuffer;

@FunctionalInterface
public interface ServerRequestHandler
{
    boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length, long requestId);
}
