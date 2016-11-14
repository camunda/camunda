package org.camunda.tngp.broker.taskqueue;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder.TasksEncoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskWriter;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

public class LockedTaskBatchWriter implements BufferWriter
{
    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected LockedTaskBatchEncoder bodyEncoder = new LockedTaskBatchEncoder();

    protected int consumerId;

    // TODO: make configurable=?
    protected static final int MAX_TASK_ENCODED_LENGTH = 1024 * 1024;
    protected UnsafeBuffer tasksBuffer = new UnsafeBuffer(new byte[MAX_TASK_ENCODED_LENGTH]);
    protected int tasksBufferLimit = 0;
    protected int numTasks = 0;

    public LockedTaskBatchWriter()
    {
        reset();
    }

    @Override
    public int getLength()
    {
        int size = MessageHeaderEncoder.ENCODED_LENGTH +
                LockedTaskBatchEncoder.BLOCK_LENGTH;

        size += TasksEncoder.sbeHeaderSize() + tasksBufferLimit;

        return size;
    }

    protected void reset()
    {
        consumerId = LockedTaskBatchEncoder.consumerIdNullValue();
        tasksBufferLimit = 0;
        numTasks = 0;
    }

    public LockedTaskBatchWriter newTasks()
    {
        tasksBufferLimit = 0;
        numTasks = 0;
        return this;
    }

    public LockedTaskBatchWriter appendTask(
            LockedTaskWriter taskWriter)
    {
        final int taskLength = taskWriter.getLength();

        // TODO: check if fits

        taskWriter.write(tasksBuffer, tasksBufferLimit);

        tasksBufferLimit += taskLength;
        numTasks++;

        return this;
    }

    public LockedTaskBatchWriter consumerId(int consumerId)
    {
        this.consumerId = consumerId;
        return this;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(0)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(0)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder
            .wrap(buffer, offset + headerEncoder.encodedLength())
            .consumerId(consumerId);

        bodyEncoder.tasksCount(numTasks);

        buffer.putBytes(bodyEncoder.limit(), tasksBuffer, 0, tasksBufferLimit);

        reset();

    }

}
