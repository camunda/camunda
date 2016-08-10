package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceReader implements BufferReader
{
    public static final int TASK_TYPE_MAXLENGTH = 256;
    public static final int PAYLOAD_MAXLENGTH = 1024 * 16;

    public static final int MAX_LENGTH;

    static
    {
        int maxLength = 0;

        maxLength += MessageHeaderDecoder.ENCODED_LENGTH;
        maxLength += TaskInstanceDecoder.BLOCK_LENGTH;
        maxLength += TASK_TYPE_MAXLENGTH;
        maxLength += PAYLOAD_MAXLENGTH;

        MAX_LENGTH = maxLength;
    }

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final TaskInstanceDecoder decoder = new TaskInstanceDecoder();

    protected final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    protected int length;

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        this.length = length;

        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();

        final int taskTypeLength = decoder.taskTypeLength();

        offset += TaskInstanceDecoder.taskTypeHeaderLength();

        taskTypeBuffer.wrap(buffer, offset, taskTypeLength);

        offset += taskTypeLength;
        decoder.limit(offset);

        final int payloadLength = decoder.payloadLength();

        offset += TaskInstanceDecoder.payloadHeaderLength();

        if (payloadLength > 0)
        {
            payloadBuffer.wrap(buffer, offset, payloadLength);
        }
        else
        {
            payloadBuffer.wrap(0, 0);
        }
    }

    public int resourceId()
    {
        return headerDecoder.resourceId();
    }

    public int shardId()
    {
        return headerDecoder.shardId();
    }

    public long id()
    {
        return decoder.id();
    }

    public int version()
    {
        return decoder.version();
    }

    public long prevVersionPosition()
    {
        return decoder.prevVersionPosition();
    }

    public TaskInstanceState state()
    {
        return decoder.state();
    }

    public long lockTime()
    {
        return decoder.lockTime();
    }

    public long lockOwnerId()
    {
        return decoder.lockOwnerId();
    }

    public long taskTypeHash()
    {
        return decoder.taskTypeHash();
    }

    public long wfActivityInstanceEventKey()
    {
        return decoder.wfActivityInstanceEventKey();
    }

    public int wfRuntimeResourceId()
    {
        return decoder.wfRuntimeResourceId();
    }

    public DirectBuffer getPayload()
    {
        return payloadBuffer;
    }

    public DirectBuffer getTaskType()
    {
        return taskTypeBuffer;
    }

    public int length()
    {
        return length;
    }

}
