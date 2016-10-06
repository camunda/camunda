package org.camunda.tngp.protocol.taskqueue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public class CreateTaskSubscriptionRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected CreateTaskSubscriptionDecoder bodyDecoder = new CreateTaskSubscriptionDecoder();

    protected UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();

        final int taskTypeLength = bodyDecoder.taskTypeLength();
        offset += CreateTaskSubscriptionDecoder.taskTypeHeaderLength();
        taskTypeBuffer.wrap(buffer, offset, taskTypeLength);
    }

    public int consumerId()
    {
        return bodyDecoder.consumerId();
    }

    public long lockDuration()
    {
        return bodyDecoder.lockDuration();
    }

    public long initialCredits()
    {
        return bodyDecoder.initialCredits();
    }

    public DirectBuffer taskType()
    {
        return taskTypeBuffer;
    }


}
