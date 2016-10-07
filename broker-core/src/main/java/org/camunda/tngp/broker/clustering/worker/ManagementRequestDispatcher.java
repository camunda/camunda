package org.camunda.tngp.broker.clustering.worker;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementDataFrameHandler;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementRequestHandler;
import org.camunda.tngp.management.gossip.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestHandler;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class ManagementRequestDispatcher implements AsyncRequestHandler
{
    public static final long REQUEST_NOT_HANDLED_RESPONSE = -3L;

    protected final MessageHeaderDecoder decoderFlyweight = new MessageHeaderDecoder();

    protected final int schemaId;

    protected final Int2ObjectHashMap<ManagementRequestHandler> requestHandlers = new Int2ObjectHashMap<>();
    protected final Int2ObjectHashMap<ManagementDataFrameHandler> dataFrameHandlers = new Int2ObjectHashMap<>();

    public ManagementRequestDispatcher(
            final int schemaId,
            final ManagementRequestHandler[] requestHandlers)
    {
        this(schemaId, requestHandlers, new ManagementDataFrameHandler[] {});
    }

    public ManagementRequestDispatcher(
            final int schemaId,
            final ManagementRequestHandler[] requestHandlers,
            final ManagementDataFrameHandler[] dataFrameHandlers)
    {
        this.schemaId = schemaId;

        for (final ManagementRequestHandler handler : requestHandlers)
        {
            this.requestHandlers.put(handler.getTemplateId(), handler);
        }

        for (final ManagementDataFrameHandler handler : dataFrameHandlers)
        {
            this.dataFrameHandlers.put(handler.getTemplateId(), handler);
        }
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
            if (templateId >= 0)
            {
                final ManagementRequestHandler handler = requestHandlers.get(templateId);

                if (handler != null)
                {
                    requestResult = handler.onRequest(
                            buffer,
                            offset,
                            length,
                            response);
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
    public long onDataFrame(final DirectBuffer buffer, final int offset, final int length, final int channelId)
    {
        decoderFlyweight.wrap(buffer, offset);

        final int templateId = decoderFlyweight.templateId();
        final int schemaId = decoderFlyweight.schemaId();

        if (schemaId == this.schemaId)
        {
            if (templateId >= 0)
            {
                final ManagementDataFrameHandler handler = dataFrameHandlers.get(templateId);

                if (handler != null)
                {
                    return handler.onDataFrame(buffer, offset, length, channelId);
                }
            }
        }

        return -1L;
    }

}
