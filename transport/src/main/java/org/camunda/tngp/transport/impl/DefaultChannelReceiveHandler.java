package org.camunda.tngp.transport.impl;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.ChannelReceiveHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class DefaultChannelReceiveHandler implements ChannelReceiveHandler
{
    protected final Dispatcher receiveBuffer;

    public DefaultChannelReceiveHandler(Dispatcher receiveBuffer)
    {
        this.receiveBuffer = receiveBuffer;
    }

    @Override
    public long onMessage(DirectBuffer buffer, int offset, int length, int channelId)
    {
        return publish(receiveBuffer, buffer, length, channelId);
    }

    public static long publish(Dispatcher receiveBuffer, DirectBuffer buffer, int length, int channelId)
    {
        long publishResult;
        int attempts = 0;

        do
        {
            publishResult = receiveBuffer.offer(
                    buffer,
                    HEADER_LENGTH,
                    length,
                    channelId);
        }
        while(publishResult == -2 || (publishResult == -1 && ++attempts <= 25));

        return publishResult;
    }

}