package org.camunda.tngp.broker.taskqueue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.broker.taskqueue.request.handler.TaskTypeHash;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

public class TaskInstanceWriter extends LogEntryWriter<TaskInstanceWriter, TaskInstanceEncoder>
{

    protected long id;
    protected long wfActivityInstanceEventKey;
    protected int wfRuntimeResourceId;
    protected long prevVersionPosition;
    protected TaskInstanceState state;
    protected long lockTime;
    protected long lockOwner;
    protected UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    public TaskInstanceWriter()
    {
        super(new TaskInstanceEncoder());
        reset();
    }

    @Override
    protected int getBodyLength()
    {
        return TaskInstanceEncoder.BLOCK_LENGTH +
                TaskInstanceEncoder.taskTypeHeaderLength() +
                taskTypeBuffer.capacity() +
                TaskInstanceEncoder.payloadHeaderLength() +
                payloadBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        final long taskTypeHashCode =
                Integer.toUnsignedLong(TaskTypeHash.hashCode(taskTypeBuffer, 0, taskTypeBuffer.capacity()));

        bodyEncoder.wrap(buffer, offset)
            .id(id)
            .version(1)
            .state(state)
            .lockTime(lockTime)
            .lockOwnerId(lockOwner)
            .prevVersionPosition(prevVersionPosition)
            .taskTypeHash(taskTypeHashCode)
            .wfActivityInstanceEventKey(wfActivityInstanceEventKey)
            .wfRuntimeResourceId(wfRuntimeResourceId)
            .putTaskType(taskTypeBuffer, 0, taskTypeBuffer.capacity())
            .putPayload(payloadBuffer, 0, payloadBuffer.capacity());

        reset();
    }

    protected void reset()
    {
        this.lockOwner = TaskInstanceEncoder.lockOwnerIdNullValue();
        this.lockTime = TaskInstanceEncoder.lockTimeNullValue();
        wfActivityInstanceEventKey = TaskInstanceEncoder.wfActivityInstanceEventKeyNullValue();
        wfRuntimeResourceId = TaskInstanceEncoder.wfRuntimeResourceIdNullValue();
    }

    public TaskInstanceWriter id(long id)
    {
        this.id = id;
        return this;
    }

    public TaskInstanceWriter wfRuntimeResourceId(int wfRuntimeResourceId)
    {
        this.wfRuntimeResourceId = wfRuntimeResourceId;
        return this;
    }

    public TaskInstanceWriter wfActivityInstanceEventKey(long wfActivityInstanceEventKey)
    {
        this.wfActivityInstanceEventKey = wfActivityInstanceEventKey;
        return this;
    }

    public TaskInstanceWriter prevVersionPosition(long prevVersionPosition)
    {
        this.prevVersionPosition = prevVersionPosition;
        return this;
    }

    public TaskInstanceWriter state(TaskInstanceState state)
    {
        this.state = state;
        return this;
    }

    public TaskInstanceWriter lockTime(long lockTime)
    {
        this.lockTime = lockTime;
        return this;
    }

    public TaskInstanceWriter lockOwner(long lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    public TaskInstanceWriter taskType(DirectBuffer buffer, int offset, int length)
    {
        taskTypeBuffer.wrap(buffer, offset, length);
        return this;
    }

    public TaskInstanceWriter payload(DirectBuffer buffer, int offset, int length)
    {
        payloadBuffer.wrap(buffer, offset, length);
        return this;
    }



}
