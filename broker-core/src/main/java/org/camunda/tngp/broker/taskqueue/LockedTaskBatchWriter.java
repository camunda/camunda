package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder.TasksEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class LockedTaskBatchWriter implements BufferWriter
{
    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected LockedTaskBatchEncoder bodyEncoder = new LockedTaskBatchEncoder();

    protected int consumerId;
    protected long lockTime;

    // TODO: make configurable=?
    protected static final int MAX_TASK_ENCODED_LENGTH = 1024 * 1024;
    protected UnsafeBuffer tasksBuffer = new UnsafeBuffer(new byte[MAX_TASK_ENCODED_LENGTH]);
    protected int tasksBufferLimit = 0;
    protected int numTasks = 0;

    @Override
    public int getLength()
    {
        int size = MessageHeaderEncoder.ENCODED_LENGTH +
                LockedTaskBatchEncoder.BLOCK_LENGTH;

        size += TasksEncoder.sbeHeaderSize() +
                ((TasksEncoder.sbeBlockLength() + TasksEncoder.payloadHeaderLength()) * numTasks);

        final int payloadLength = tasksBufferLimit - ((Long.BYTES + Integer.BYTES) * numTasks);
        size += payloadLength;

        return size;
    }

    public LockedTaskBatchWriter newTasks()
    {
        tasksBufferLimit = 0;
        numTasks = 0;
        return this;
    }

    public LockedTaskBatchWriter appendTask(long taskId, DirectBuffer payloadBuffer, int payloadOffset, int payloadLength)
    {
        // TODO: check if still fits?

        tasksBuffer.putLong(tasksBufferLimit, taskId);
        tasksBufferLimit += Long.BYTES;

        tasksBuffer.putInt(tasksBufferLimit, payloadLength);
        tasksBufferLimit += Integer.BYTES;

        tasksBuffer.putBytes(tasksBufferLimit, payloadBuffer, payloadOffset, payloadLength);
        tasksBufferLimit += payloadLength;

        numTasks++;

        return this;
    }

    public LockedTaskBatchWriter consumerId(int consumerId)
    {
        this.consumerId = consumerId;
        return this;
    }

    public LockedTaskBatchWriter lockTime(long lockTime)
    {
        this.lockTime = lockTime;
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
            .consumerId(consumerId)
            .lockTime(lockTime);

        final TasksEncoder tasksEncoder = bodyEncoder.tasksCount(numTasks);

        int currentLimit = 0;
        for (int i = 0; i < numTasks; i++)
        {
            final long taskId = tasksBuffer.getLong(currentLimit);
            currentLimit += Long.BYTES;

            final int payloadLength = tasksBuffer.getInt(currentLimit);
            currentLimit += Integer.BYTES;

            tasksEncoder.next().taskId(taskId).putPayload(tasksBuffer, currentLimit, payloadLength);
            currentLimit += payloadLength;
        }

    }

}
