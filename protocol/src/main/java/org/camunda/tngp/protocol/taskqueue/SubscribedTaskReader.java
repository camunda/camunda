package org.camunda.tngp.protocol.taskqueue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public class SubscribedTaskReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SubscribedTaskDecoder bodyDecoder = new SubscribedTaskDecoder();

    protected UnsafeBuffer payload = new UnsafeBuffer(0, 0);

    public long subscriptionId()
    {
        return bodyDecoder.subscriptionId();
    }

    public long taskId()
    {
        return bodyDecoder.task().id();
    }

    public long lockTime()
    {
        return bodyDecoder.task().lockTime();
    }

    public long wfInstanceId()
    {
        return bodyDecoder.task().wfInstanceId();
    }

    public DirectBuffer payload()
    {
        return payload;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        offset += headerDecoder.blockLength();
        offset += SubscribedTaskDecoder.taskPayloadHeaderLength();

        final int payloadLength = bodyDecoder.taskPayloadLength();
        if (payloadLength > 0)
        {
            payload.wrap(buffer, offset, payloadLength);
        }
        else
        {
            payload.wrap(0, 0);
        }
    }
}
