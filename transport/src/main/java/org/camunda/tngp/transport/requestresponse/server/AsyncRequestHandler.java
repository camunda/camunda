package org.camunda.tngp.transport.requestresponse.server;

import uk.co.real_logic.agrona.DirectBuffer;

public interface AsyncRequestHandler
{
    long onRequest(
            long requestId,
            int channelId,
            DirectBuffer buffer,
            int offset,
            int length,
            int blockLength,
            int version);

}
