package org.camunda.tngp.client.impl.cmd.taskqueue;

import org.camunda.tngp.client.impl.cmd.ClientRequestWriter;
import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CreateTaskRequestWriter implements ClientRequestWriter
{
    protected final UnsafeBuffer payload = new UnsafeBuffer(0, 0);

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final CreateTaskInstanceEncoder bodyEncoder = new CreateTaskInstanceEncoder();

    protected int resourceId;
    protected int shardId;

    protected UnsafeBuffer taskType = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
               bodyEncoder.sbeBlockLength() +
               CreateTaskInstanceEncoder.payloadHeaderLength() +
               taskType.capacity() +
               CreateTaskInstanceEncoder.payloadHeaderLength() +
               payload.capacity();
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            .resourceId(resourceId)
            .shardId(shardId);

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset);

        bodyEncoder.putTaskType(taskType, 0, taskType.capacity());

        bodyEncoder.putPayload(payload, 0, payload.capacity());

        taskType.wrap(0, 0);
        payload.wrap(0, 0);
    }

    @Override
    public void validate()
    {
        // TODO
    }

    public CreateTaskRequestWriter resourceId(int resourceId)
    {
        this.resourceId = resourceId;
        return this;
    }

    public CreateTaskRequestWriter shardId(int shardId)
    {
        this.shardId = shardId;
        return this;
    }

    public UnsafeBuffer getPayload()
    {
        return payload;
    }

    public UnsafeBuffer getTaskType()
    {
        return taskType;
    }
}
