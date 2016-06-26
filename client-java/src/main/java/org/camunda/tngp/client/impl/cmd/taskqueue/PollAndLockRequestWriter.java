package org.camunda.tngp.client.impl.cmd.taskqueue;

import org.camunda.tngp.client.impl.cmd.ClientRequestWriter;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksEncoder;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class PollAndLockRequestWriter implements ClientRequestWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final PollAndLockTasksEncoder requestEncoder = new PollAndLockTasksEncoder();

    protected int resourceId;
    protected int shardId;

    protected final UnsafeBuffer taskType = new UnsafeBuffer(0, 0);
    protected long lockTimeMs;
    protected int maxTasks;

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                requestEncoder.sbeBlockLength() +
                PollAndLockTasksEncoder.taskTypeHeaderLength() +
                taskType.capacity();
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
            .shardId(0);

        offset += headerEncoder.encodedLength();

        requestEncoder.wrap(buffer, offset)
            .lockTime(lockTimeMs)
            .maxTasks(maxTasks)
            .putTaskType(taskType, 0, taskType.capacity());

        taskType.wrap(0, 0);
    }

    @Override
    public void validate()
    {
        // TODO
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

    public UnsafeBuffer getTaskType()
    {
        return taskType;
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
