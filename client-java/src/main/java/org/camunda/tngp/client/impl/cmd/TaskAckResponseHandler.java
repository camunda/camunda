package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.protocol.taskqueue.AckDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;

import uk.co.real_logic.agrona.DirectBuffer;

public class TaskAckResponseHandler implements ClientResponseHandler<Long>
{
    protected final AckDecoder ackDecoder = new AckDecoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @Override
    public int getResponseSchemaId()
    {
        return AckDecoder.SCHEMA_ID;
    }

    @Override
    public int getResponseTemplateId()
    {
        return AckDecoder.TEMPLATE_ID;
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
