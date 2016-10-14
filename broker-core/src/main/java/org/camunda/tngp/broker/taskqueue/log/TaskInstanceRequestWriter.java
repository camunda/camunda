package org.camunda.tngp.broker.taskqueue.log;

import org.agrona.MutableDirectBuffer;
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

    @Override
    protected int getBodyLength()
    {
        return TaskInstanceRequestEncoder.BLOCK_LENGTH;
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key)
            .lockOwnerId(lockOwnerId)
            .type(type);
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

}
