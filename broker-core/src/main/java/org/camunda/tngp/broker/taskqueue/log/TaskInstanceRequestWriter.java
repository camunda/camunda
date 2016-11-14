package org.camunda.tngp.broker.taskqueue.log;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.protocol.log.TaskInstanceRequestEncoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestType;

public class TaskInstanceRequestWriter extends LogEntryWriter<TaskInstanceRequestWriter, TaskInstanceRequestEncoder>
{

    public TaskInstanceRequestWriter()
    {
        super(new TaskInstanceRequestEncoder());
    }

    protected long key;
    protected long lockOwnerId;
    protected TaskInstanceRequestType type;
    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    protected int getBodyLength()
    {
        return TaskInstanceRequestEncoder.BLOCK_LENGTH +
                TaskInstanceRequestEncoder.payloadHeaderLength() +
                payloadBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key)
            .lockOwnerId(lockOwnerId)
            .type(type)
            .putPayload(payloadBuffer, 0, payloadBuffer.capacity());
    }

    public TaskInstanceRequestWriter type(TaskInstanceRequestType type)
    {
        this.type = type;
        return this;
    }

    public TaskInstanceRequestWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public TaskInstanceRequestWriter lockOwnerId(long lockOwnerId)
    {
        this.lockOwnerId = lockOwnerId;
        return this;
    }

    public TaskInstanceRequestWriter payload(DirectBuffer payload, int offset, int length)
    {
        this.payloadBuffer.wrap(payload, offset, length);
        return this;
    }

}
