package org.camunda.tngp.broker.clustering.worker;

import org.agrona.DirectBuffer;
import org.camunda.tngp.management.gossip.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestHandler;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class CompositeManagementRequestDispatcher implements AsyncRequestHandler
{
    protected final MessageHeaderDecoder decoderFlyweight = new MessageHeaderDecoder();

    protected final ManagementRequestDispatcher[] dispatchers;

    public CompositeManagementRequestDispatcher(ManagementRequestDispatcher[] dispatchers)
    {
        this.dispatchers = dispatchers;
    }

    @Override
    public long onRequest(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        decoderFlyweight.wrap(buffer, offset);

        final int schemaId = decoderFlyweight.schemaId();

        for (int i = 0; i < dispatchers.length; i++)
        {
            if (dispatchers[i].schemaId == schemaId)
            {
                return dispatchers[i].onRequest(buffer, offset, length, response);
            }
        }

        return -1;
    }

    @Override
    public long onDataFrame(DirectBuffer buffer, int offset, int length, int channelId)
    {
        decoderFlyweight.wrap(buffer, offset);

        final int schemaId = decoderFlyweight.schemaId();

        for (int i = 0; i < dispatchers.length; i++)
        {
            if (dispatchers[i].schemaId == schemaId)
            {
                return dispatchers[i].onDataFrame(buffer, offset, length, channelId);
            }
        }

        return -1;
    }

}
