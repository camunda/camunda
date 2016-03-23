package org.camunda.tngp.transport.protocol;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.ChannelReceiveHandler;
import static org.camunda.tngp.transport.impl.DefaultChannelReceiveHandler.publish;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class ProtocolDemux implements ChannelReceiveHandler
{
    protected final Int2ObjectHashMap<Dispatcher> dispatcherMap;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    public ProtocolDemux(Int2ObjectHashMap<Dispatcher> dispatcherMap)
    {
        this.dispatcherMap = dispatcherMap;
    }

    @Override
    public long onMessage(DirectBuffer buffer, int offset, int length, int channelId)
    {
        headerDecoder.wrap(buffer, offset);

        final int schemaId = headerDecoder.schemaId();
        final Dispatcher dispatcher = dispatcherMap.get(schemaId);

        long publishResult = -1;

        if(dispatcher != null)
        {
            publishResult = publish(dispatcher, buffer, length, channelId);
        }

        return publishResult;
    }
}
