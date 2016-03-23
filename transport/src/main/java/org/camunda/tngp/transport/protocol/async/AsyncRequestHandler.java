package org.camunda.tngp.transport.protocol.async;

import uk.co.real_logic.agrona.DirectBuffer;

public interface AsyncRequestHandler
{
    int getTemplateId();

    long onRequest(
            long requestId,
            int channelId,
            DirectBuffer buffer,
            int offset,
            int length,
            int blockLength,
            int version);

    void onAsyncWorkCompleted(
            DeferredMessage deferredResponse,
            DirectBuffer asyncWorkBuffer,
            int offset,
            int length);

}
