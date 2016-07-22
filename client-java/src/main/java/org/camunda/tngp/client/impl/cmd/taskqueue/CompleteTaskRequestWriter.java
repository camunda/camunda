package org.camunda.tngp.client.impl.cmd.taskqueue;

import java.nio.ByteBuffer;

import org.camunda.tngp.client.impl.cmd.PayloadRequestWriter;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CompleteTaskRequestWriter implements PayloadRequestWriter
{
    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final CompleteTaskEncoder bodyEncoder = new CompleteTaskEncoder();

    protected int resourceId;
    protected int shardId;

    protected final UnsafeBuffer payload = new UnsafeBuffer(0, 0);
    protected long taskId = CompleteTaskEncoder.taskIdNullValue();
    protected int consumerId;

    public CompleteTaskRequestWriter()
    {
        reset();
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
              bodyEncoder.sbeBlockLength() +
              CompleteTaskEncoder.payloadHeaderLength() +
              payload.capacity();
    }

    protected void reset()
    {
        resourceId = MessageHeaderEncoder.resourceIdNullValue();
        shardId = MessageHeaderEncoder.shardIdNullValue();

        payload.wrap(0, 0);
        taskId = CompleteTaskEncoder.taskIdNullValue();
        consumerId = 0;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .schemaId(bodyEncoder.sbeSchemaId())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion())
            .resourceId(resourceId)
            .shardId(shardId);

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .taskId(taskId)
            .consumerId(consumerId)
            .putPayload(payload, 0, payload.capacity());

        reset();
    }

    @Override
    public void validate()
    {
        if (taskId == CompleteTaskEncoder.taskIdNullValue())
        {
            throw new RuntimeException("No task id set");
        }

        if (resourceId == MessageHeaderEncoder.resourceIdNullValue())
        {
            throw new RuntimeException("No task queue id set");
        }
    }


    public CompleteTaskRequestWriter resourceId(int resourceId)
    {
        this.resourceId = resourceId;
        return this;
    }

    public CompleteTaskRequestWriter shardId(int shardId)
    {
        this.shardId = shardId;
        return this;
    }

    @Override
    public void payload(byte[] bytes, int offset, int length)
    {
        payload.wrap(bytes, offset, length);
    }

    @Override
    public void payload(DirectBuffer buffer, int offset, int length)
    {
        payload.wrap(buffer, offset, length);
    }

    @Override
    public void payload(ByteBuffer byteBuffer)
    {
        payload.wrap(byteBuffer);
    }

    public CompleteTaskRequestWriter taskId(long taskId)
    {
        this.taskId = taskId;
        return this;
    }
}
