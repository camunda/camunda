package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseDecoder;

public class TaskAckResponseHandler implements ClientResponseHandler<Long>
{
    protected final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    @Override
    public int getResponseSchemaId()
    {
        return responseDecoder.sbeSchemaId();
    }

    @Override
    public int getResponseTemplateId()
    {
        return responseDecoder.sbeTemplateId();
    }

    @Override
    public Long readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        responseDecoder.wrap(responseBuffer, offset, length, responseDecoder.sbeSchemaVersion());

        final long taskKey = responseDecoder.longKey();

        return taskKey;
    }

}
