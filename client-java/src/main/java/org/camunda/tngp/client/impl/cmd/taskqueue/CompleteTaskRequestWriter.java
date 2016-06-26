package org.camunda.tngp.client.impl.cmd.taskqueue;

import org.camunda.tngp.client.impl.cmd.ClientRequestWriter;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CompleteTaskRequestWriter implements ClientRequestWriter
{
    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final CompleteTaskEncoder bodyEncoder = new CompleteTaskEncoder();

    protected int resourceId;
    protected int shardId;

    protected final UnsafeBuffer payload = new UnsafeBuffer(0, 0);
    protected long taskId;

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
              bodyEncoder.sbeBlockLength() +
              CompleteTaskEncoder.payloadHeaderLength() +
              payload.capacity();
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
            .putPayload(payload, 0, payload.capacity());
    }

    @Override
    public void validate()
    {
        // TODO
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

    public UnsafeBuffer getPayload()
    {
        return payload;
    }

    public CompleteTaskRequestWriter taskId(long taskId)
    {
        this.taskId = taskId;
        return this;
    }
}
