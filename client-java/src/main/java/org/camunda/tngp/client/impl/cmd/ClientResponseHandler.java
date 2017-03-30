package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;

public interface ClientResponseHandler<R>
{

    int getResponseSchemaId();

    int getResponseTemplateId();

    R readResponse(int channelId, DirectBuffer responseBuffer, int offset, int blockLength, int version);

}
