package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckDecoder;

import org.agrona.DirectBuffer;

public class TaskAckResponseHandler implements ClientResponseHandler<Long>
{
    protected final SingleTaskAckDecoder ackDecoder = new SingleTaskAckDecoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @Override
    public int getResponseSchemaId()
    {
        return SingleTaskAckDecoder.SCHEMA_ID;
    }

    @Override
    public int getResponseTemplateId()
    {
        return SingleTaskAckDecoder.TEMPLATE_ID;
    }

    @Override
    public Long readResponse(DirectBuffer responseBuffer, int offset, int length)
    {

        headerDecoder.wrap(responseBuffer, offset);

        offset += headerDecoder.encodedLength();

        ackDecoder.wrap(responseBuffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        return ackDecoder.taskId();
    }

}
