package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.TaskSubscriptionAckDecoder;

public class TaskSubscriptionResponseHandler implements ClientResponseHandler<Long>
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final TaskSubscriptionAckDecoder responseDecoder = new TaskSubscriptionAckDecoder();

    @Override
    public int getResponseSchemaId()
    {
        return TaskSubscriptionAckDecoder.SCHEMA_ID;
    }

    @Override
    public int getResponseTemplateId()
    {
        return TaskSubscriptionAckDecoder.TEMPLATE_ID;
    }

    @Override
    public Long readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        headerDecoder.wrap(responseBuffer, offset);

        offset += headerDecoder.encodedLength();

        responseDecoder.wrap(responseBuffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        return responseDecoder.id();
    }

}
