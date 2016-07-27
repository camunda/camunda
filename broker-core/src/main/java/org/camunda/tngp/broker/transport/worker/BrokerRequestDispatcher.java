package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestHandler;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import uk.co.real_logic.agrona.DirectBuffer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BrokerRequestDispatcher<C extends ResourceContext> implements AsyncRequestHandler
{
    public static final long REQUEST_NOT_HANDLED_RESPONSE = -3L;

    protected final MessageHeaderDecoder decoderFlyweight = new MessageHeaderDecoder();

    protected final int schemaId;

    protected final BrokerRequestHandler[] handlers;

    protected ResourceContextProvider<C> contextProvider;

    public BrokerRequestDispatcher(
            ResourceContextProvider<C> contextProvider,
            int schemaId,
            BrokerRequestHandler[] handlers)
    {
        this.contextProvider = contextProvider;
        this.handlers = handlers;
        this.schemaId = schemaId;
    }

    @Override
    public long onRequest(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        decoderFlyweight.wrap(buffer, offset);

        final int templateId = decoderFlyweight.templateId();
        final int schemaId = decoderFlyweight.schemaId();

        long requestResult = -1;

        if (schemaId == this.schemaId)
        {
            if (templateId >= 0 && templateId < handlers.length)
            {
                final BrokerRequestHandler<C> handler = handlers[templateId];

                if (handler != null)
                {
                    final int resourceId = decoderFlyweight.resourceId();
                    final C ctx = contextProvider.getContextForResource(resourceId);
                    // TODO: shard

                    if (ctx != null)
                    {
                        requestResult = handler.onRequest(
                                ctx,
                                buffer,
                                offset,
                                length,
                                response);
                    }
                }
            }
        }
        else
        {
            // ignore
            requestResult = REQUEST_NOT_HANDLED_RESPONSE;
        }

        return requestResult;
    }
}
