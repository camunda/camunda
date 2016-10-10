package org.camunda.tngp.protocol.taskqueue;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public class SubscribedTaskReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SubscribedTaskDecoder bodyDecoder = new SubscribedTaskDecoder();

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

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
    }
}
