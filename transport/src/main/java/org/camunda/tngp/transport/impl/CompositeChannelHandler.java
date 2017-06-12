package org.camunda.tngp.transport.impl;

import org.agrona.DirectBuffer;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class CompositeChannelHandler implements TransportChannelHandler
{

    protected TransportChannelHandler[] handlers = Protocols.handlerForAllProtocols(new DefaultChannelHandler());

    /**
     * Handlers must be registered according to the protocol IDs defined in {@link Protocols}.
     *
     * @param protocolId see {@link Protocols}
     * @param handler guess what, it is going to handle messages of that protocol
     */
    public void addHandler(short protocolId, TransportChannelHandler handler)
    {
        handlers[protocolId] = handler;
    }

    @Override
    public void onChannelOpened(Channel transportChannel)
    {
        for (int i = 0; i < handlers.length; i++)
        {
            handlers[i].onChannelOpened(transportChannel);
        }
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
        for (int i = 0; i < handlers.length; i++)
        {
            handlers[i].onChannelClosed(transportChannel);
        }
    }


    @Override
    public void onChannelInterrupted(Channel transportChannel)
    {
        for (int i = 0; i < handlers.length; i++)
        {
            handlers[i].onChannelInterrupted(transportChannel);
        }
    }

    @Override
    public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        final TransportChannelHandler handler = getHandler(buffer, offset, length);
        handler.onChannelSendError(transportChannel, buffer, offset, length);
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        final TransportChannelHandler handler = getHandler(buffer, offset, length);
        return handler.onChannelReceive(transportChannel, buffer, offset, length);
    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        final TransportChannelHandler handler = getHandler(buffer, DataFrameDescriptor.HEADER_LENGTH + offset, length - DataFrameDescriptor.HEADER_LENGTH);
        return handler.onControlFrame(transportChannel, buffer, offset, length);
    }

    protected TransportChannelHandler getHandler(DirectBuffer message, int protocolHeaderOffset, int length)
    {
        final short protocolId = message.getShort(TransportHeaderDescriptor.protocolIdOffset(protocolHeaderOffset));

        if (protocolId >= handlers.length)
        {
            throw new RuntimeException("Invalid protocol id " + protocolId);
        }

        return handlers[protocolId];
    }
}
