package org.camunda.tngp.broker.transport.worker;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.camunda.tngp.broker.transport.worker.spi.BrokerDataFrameHandler;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestHandler;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BrokerRequestDispatcher<C extends ResourceContext> implements AsyncRequestHandler
{
    public static final long REQUEST_NOT_HANDLED_RESPONSE = -3L;

    protected final MessageHeaderDecoder decoderFlyweight = new MessageHeaderDecoder();

    protected final int schemaId;

    protected final Int2ObjectHashMap<BrokerRequestHandler<C>> handlersByTemplateId = new Int2ObjectHashMap<>();
    protected final Long2ObjectHashMap<BrokerDataFrameHandler> dataFrameHandlers;

    protected ResourceContextProvider<C> contextProvider;

    public BrokerRequestDispatcher(
            ResourceContextProvider<C> contextProvider,
            int schemaId,
            BrokerRequestHandler[] handlers)
    {
        this.contextProvider = contextProvider;
        this.schemaId = schemaId;

        for (BrokerRequestHandler<C> handler : handlers)
        {
            this.handlersByTemplateId.put(handler.getTemplateId(), handler);
        }
        this.dataFrameHandlers = new Long2ObjectHashMap<>();
    }

    public void addDataFrameHandler(int templateId, BrokerDataFrameHandler handler)
    {
        if (dataFrameHandlers.containsKey(templateId))
        {
            throw new RuntimeException("Handler with id " + templateId + " already registered");
        }

        dataFrameHandlers.put(templateId, handler);
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
        final int resourceId = decoderFlyweight.resourceId();

        long requestResult = -1;

        if (schemaId == this.schemaId)
        {
            if (templateId >= 0)
            {
                final BrokerRequestHandler<C> handler = handlersByTemplateId.get(templateId);

                if (handler != null)
                {
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

    @Override
    public long onDataFrame(DirectBuffer buffer, int offset, int length)
    {
        decoderFlyweight.wrap(buffer, offset);

        final int templateId = decoderFlyweight.templateId();
        final int schemaId = decoderFlyweight.schemaId();
        final int resourceId = decoderFlyweight.resourceId();

        if (schemaId == this.schemaId)
        {
            if (templateId >= 0 && dataFrameHandlers.containsKey(templateId))
            {
                final C ctx = contextProvider.getContextForResource(resourceId);

                if (ctx != null)
                {
                    final BrokerDataFrameHandler handler = dataFrameHandlers.get(templateId);
                    return handler.onDataFrame(ctx, buffer, offset, length);
                }
            }
        }

        return -1L;
    }
}
