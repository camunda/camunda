package org.camunda.tngp.broker.transport.clientapi;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.impl.DefaultChannelHandler;

public class ClientApiChannelHandler extends DefaultChannelHandler
{
    protected final ClientApiMessageHandler messageHandler;

    public ClientApiChannelHandler(ClientApiMessageHandler messageHandler)
    {
        this.messageHandler = messageHandler;
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        return messageHandler.handleMessage(transportChannel, buffer, offset, length);
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
        messageHandler.onChannelClose(transportChannel.getStreamId());
    }
}
