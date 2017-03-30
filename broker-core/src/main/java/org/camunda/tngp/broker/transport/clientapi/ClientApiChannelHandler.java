package org.camunda.tngp.broker.transport.clientapi;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.impl.DefaultChannelHandler;

public class ClientApiChannelHandler extends DefaultChannelHandler
{
    protected final ClientApiMessageHandler messageHandler;

    public ClientApiChannelHandler(ClientApiMessageHandler messageHandler)
    {
        this.messageHandler = messageHandler;
    }

    @Override
    public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        return messageHandler.handleMessage(transportChannel, buffer, offset, length);
    }

    @Override
    public void onChannelClosed(TransportChannel transportChannel)
    {
        messageHandler.onChannelClose(transportChannel.getId());
    }
}
