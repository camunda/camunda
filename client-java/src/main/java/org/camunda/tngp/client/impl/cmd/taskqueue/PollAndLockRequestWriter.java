package org.camunda.tngp.client.impl.cmd.taskqueue;

import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksEncoder;
import org.camunda.tngp.util.buffer.RequestWriter;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PollAndLockRequestWriter implements RequestWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final PollAndLockTasksEncoder requestEncoder = new PollAndLockTasksEncoder();

    protected int resourceId;
    protected int shardId;

    protected final UnsafeBuffer taskType = new UnsafeBuffer(0, 0);
    protected long lockTimeMs;
    protected int maxTasks;

    public PollAndLockRequestWriter()
    {
        reset();
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                requestEncoder.sbeBlockLength() +
                PollAndLockTasksEncoder.taskTypeHeaderLength() +
                taskType.capacity();
    }

    protected void reset()
    {
        resourceId = MessageHeaderEncoder.resourceIdNullValue();
        shardId = MessageHeaderEncoder.shardIdNullValue();
        lockTimeMs = PollAndLockTasksEncoder.lockTimeNullValue();
        maxTasks = PollAndLockTasksEncoder.maxTasksNullValue();
        taskType.wrap(0, 0);
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(requestEncoder.sbeBlockLength())
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaVersion())
            .resourceId(resourceId)
            .shardId(shardId);

        offset += headerEncoder.encodedLength();

        requestEncoder.wrap(buffer, offset)
            .lockTime(lockTimeMs)
            .consumerId(0)
            .maxTasks(maxTasks)
            .putTaskType(taskType, 0, taskType.capacity());

        reset();
    }

    @Override
    public void validate()
    {
        if (taskType.capacity() == 0)
        {
            throw new RuntimeException("No task type specified");
        }

        if (maxTasks < 0)
        {
            throw new RuntimeException("maxTasks must greater than or equal to 0");
        }


        if (resourceId == MessageHeaderEncoder.resourceIdNullValue())
        {
            throw new RuntimeException("No task queue id set");
        }
    }


    public PollAndLockRequestWriter resourceId(int resourceId)
    {
        this.resourceId = resourceId;
        return this;
    }

    public PollAndLockRequestWriter shardId(int shardId)
    {
        this.shardId = shardId;
        return this;
    }

    public PollAndLockRequestWriter taskType(byte[] bytes, int offset, int length)
    {
        taskType.wrap(bytes, offset, length);
        return this;
    }

    public PollAndLockRequestWriter lockTimeMs(long lockTimeMs)
    {
        this.lockTimeMs = lockTimeMs;
        return this;
    }

    public PollAndLockRequestWriter maxTasks(int maxTasks)
    {
        this.maxTasks = maxTasks;
        return this;
    }

}
