package io.zeebe.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.impl.ClientRequestPool;
import io.zeebe.util.buffer.BufferWriter;

public class ClientOutput
{
    protected final Dispatcher sendBuffer;
    protected final ClientRequestPool requestPool;

    public ClientOutput(Dispatcher sendBuffer, ClientRequestPool requestPool)
    {
        this.sendBuffer = sendBuffer;
        this.requestPool = requestPool;
    }

    public boolean sendMessage(TransportMessage transportMessage)
    {
        return transportMessage.trySend(sendBuffer);
    }

    /**
     * Returns null if request cannot be currently written due to exhausted capacity.
     * Throws an exception if the request is not sendable at all (e.g. buffer writer throws exception).
     */
    public ClientRequest sendRequest(RemoteAddress addr, BufferWriter writer)
    {
        return requestPool.open(addr, writer);
    }

}
