package io.zeebe.transport.impl;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.TransportMessage;

public class ServerOutputImpl implements ServerOutput
{

    protected final Dispatcher sendBuffer;

    public ServerOutputImpl(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public boolean sendMessage(TransportMessage transportMessage)
    {
        return transportMessage.trySend(sendBuffer);
    }

    public boolean sendResponse(ServerResponse response)
    {
        return response.trySend(sendBuffer);
    }

}
