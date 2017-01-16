package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;

public class TaskSubscriptionResponseHandler implements ClientResponseHandler<Long>
{

    @Override
    public int getResponseSchemaId()
    {
        return -1;
    }

    @Override
    public int getResponseTemplateId()
    {
        return -1;
    }

    @Override
    public Long readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        return -1L;
    }

}
