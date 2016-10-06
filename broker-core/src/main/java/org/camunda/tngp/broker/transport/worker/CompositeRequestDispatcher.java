package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestHandler;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import org.agrona.DirectBuffer;

@SuppressWarnings("rawtypes")
public class CompositeRequestDispatcher<C extends ResourceContext> implements AsyncRequestHandler
{
    protected final MessageHeaderDecoder decoderFlyweight = new MessageHeaderDecoder();

    protected final BrokerRequestDispatcher[] dispatchers;

    public CompositeRequestDispatcher(BrokerRequestDispatcher[] dispatchers)
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
    public long onDataFrame(DirectBuffer buffer, int offset, int length)
    {
        decoderFlyweight.wrap(buffer, offset);

        final int schemaId = decoderFlyweight.schemaId();

        for (int i = 0; i < dispatchers.length; i++)
        {
            if (dispatchers[i].schemaId == schemaId)
            {
                return dispatchers[i].onDataFrame(buffer, offset, length);
            }
        }

        return -1;
    }
}
