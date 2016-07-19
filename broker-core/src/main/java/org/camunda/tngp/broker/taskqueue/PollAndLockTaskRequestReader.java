package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class PollAndLockTaskRequestReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected PollAndLockTasksDecoder bodyDecoder = new PollAndLockTasksDecoder();

    protected final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += bodyDecoder.encodedLength();
        taskTypeBuffer.wrap(buffer, offset + PollAndLockTasksDecoder.taskTypeHeaderLength(), bodyDecoder.taskTypeLength());
    }

    public int consumerId()
    {
        return bodyDecoder.consumerId();
    }

    public int maxTasks()
    {
        return bodyDecoder.maxTasks();
    }

    public long lockTime()
    {
        return bodyDecoder.lockTime();
    }

    public DirectBuffer taskType()
    {
        return taskTypeBuffer;
    }

}
