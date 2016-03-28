package org.camunda.tngp.taskqueue.protocol;

import org.camunda.tngp.transport.requestresponse.server.AsyncRequestHandler;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class SbeRequestDispatcher implements AsyncRequestHandler
{
    protected final MessageHeaderDecoder decoderFlyweight = new MessageHeaderDecoder();

    protected Int2ObjectHashMap<SbeRequestHandler> sbeRequestHandlers = new Int2ObjectHashMap<>();

    @Override
    public long onRequest(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        decoderFlyweight.wrap(buffer, offset);

        final int templateId = decoderFlyweight.templateId();
        final int blockLength = decoderFlyweight.blockLength();
        final int schemaVersion = decoderFlyweight.version();

        final SbeRequestHandler handler = sbeRequestHandlers.get(templateId);

        long requestResult = -1;

        if(handler != null)
        {
            final int messageBodyOffset = offset + decoderFlyweight.encodedLength();
            final int messageBodyLength = length - decoderFlyweight.encodedLength();

            requestResult = handler.onRequest(buffer, messageBodyOffset, messageBodyLength, response, blockLength, schemaVersion);
        }

        return requestResult;
    }

    public void reqisterHandler(SbeRequestHandler handler)
    {
        sbeRequestHandlers.put(handler.getTemplateId(), handler);
    }

}
