package org.camunda.tngp.protocol.taskqueue;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public class TaskSubscriptionReader implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final TaskSubscriptionAckDecoder bodyDecoder = new TaskSubscriptionAckDecoder();

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    }

    public long id()
    {
        return bodyDecoder.id();
    }

}
