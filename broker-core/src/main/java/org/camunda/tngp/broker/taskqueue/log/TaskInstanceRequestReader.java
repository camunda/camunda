package org.camunda.tngp.broker.taskqueue.log;

import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestType;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected TaskInstanceRequestDecoder bodyDecoder = new TaskInstanceRequestDecoder();

    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        offset += headerDecoder.blockLength();
        offset += TaskInstanceRequestDecoder.payloadHeaderLength();

        final int payloadLength = bodyDecoder.payloadLength();

        if (payloadLength > 0)
        {
            payloadBuffer.wrap(buffer, offset, payloadLength);
        }
        else
        {
            payloadBuffer.wrap(0, 0);
        }

    }

    public DirectBuffer payload()
    {
        return payloadBuffer;
    }

    public TaskInstanceRequestType type()
    {
        return bodyDecoder.type();

    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public long consumerId()
    {
        return bodyDecoder.lockOwnerId();
    }
}
