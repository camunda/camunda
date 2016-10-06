package org.camunda.tngp.protocol.taskqueue;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.util.buffer.BufferWriter;

public class TaskSubscriptionAckWriter implements BufferWriter
{

    protected long id;

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected TaskSubscriptionAckEncoder bodyEncoder = new TaskSubscriptionAckEncoder();

    public TaskSubscriptionAckWriter()
    {
        reset();
    }

    public TaskSubscriptionAckWriter id(long id)
    {
        this.id = id;
        return this;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH + TaskSubscriptionAckEncoder.BLOCK_LENGTH;
    }

    protected void reset()
    {
        id = TaskSubscriptionAckEncoder.idNullValue();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .schemaId(bodyEncoder.sbeSchemaId())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .id(id);

        reset();
    }

}
