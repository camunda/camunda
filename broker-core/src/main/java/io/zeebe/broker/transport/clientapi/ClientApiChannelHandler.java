package io.zeebe.broker.transport.clientapi;

import org.agrona.DirectBuffer;
import io.zeebe.transport.Channel;
import io.zeebe.transport.impl.DefaultChannelHandler;

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
