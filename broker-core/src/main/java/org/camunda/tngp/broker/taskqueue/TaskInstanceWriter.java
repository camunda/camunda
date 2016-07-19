package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.taskqueue.handler.TaskTypeHash;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected TaskInstanceEncoder bodyEncoder = new TaskInstanceEncoder();

    protected long id;
    protected long wfActivityInstanceEventKey;
    protected int wfRuntimeResourceId;
    protected long prevVersionPosition;
    protected TaskInstanceState state;
    protected long lockTime;
    protected long lockOwner;
    protected UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
               TaskInstanceEncoder.BLOCK_LENGTH +
               TaskInstanceEncoder.taskTypeHeaderLength() +
               taskTypeBuffer.capacity() +
               TaskInstanceEncoder.payloadHeaderLength() +
               payloadBuffer.capacity();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(TaskInstanceEncoder.BLOCK_LENGTH)
            // TODO: resource and shard ids
            .resourceId(0)
            .shardId(0)
            .schemaId(TaskInstanceEncoder.SCHEMA_ID)
            .version(TaskInstanceEncoder.SCHEMA_VERSION)
            .templateId(TaskInstanceEncoder.TEMPLATE_ID);

        offset += headerEncoder.encodedLength();

        final int taskTypeHashCode = TaskTypeHash.hashCode(taskTypeBuffer, 0, taskTypeBuffer.capacity());

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
