package io.zeebe.transport;

import io.zeebe.util.buffer.BufferWriter;

public interface ClientOutput
{

    boolean sendMessage(TransportMessage transportMessage);

    /**
     * Returns null if request cannot be currently written due to exhausted capacity.
     * Throws an exception if the request is not sendable at all (e.g. buffer writer throws exception).
     */
    ClientRequest sendRequest(RemoteAddress addr, BufferWriter writer);

}