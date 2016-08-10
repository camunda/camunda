package org.camunda.tngp.broker.wf.repository.log;

import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionWriter implements BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final WfDefinitionEncoder encoder = new WfDefinitionEncoder();

    protected int resourceId;
    protected int shardId;

    protected long id;

    protected final UnsafeBuffer typeKeyBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
               WfDefinitionEncoder.BLOCK_LENGTH +
               WfDefinitionEncoder.keyHeaderLength() +
               typeKeyBuffer.capacity() +
               WfDefinitionEncoder.resourceHeaderLength() +
               resourceBuffer.capacity();
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(encoder.sbeBlockLength())
            .templateId(encoder.sbeTemplateId())
            .schemaId(encoder.sbeSchemaId())
            .version(encoder.sbeSchemaVersion())
            .resourceId(resourceId)
            .shardId(shardId);

        offset += headerEncoder.encodedLength();

        encoder.wrap(buffer, offset)
            .id(id)
            .putKey(typeKeyBuffer, 0, typeKeyBuffer.capacity())
            .putResource(resourceBuffer, 0, resourceBuffer.capacity());

        typeKeyBuffer.wrap(0, 0);
        resourceBuffer.wrap(0, 0);
    }

    public WfDefinitionWriter resourceId(final int value)
    {
        this.resourceId = value;
        return this;
    }

    public WfDefinitionWriter shardId(final int value)
    {
        this.shardId = value;
        return this;
    }

    public WfDefinitionWriter id(final long value)
    {
        this.id = value;
        return this;
    }

    public WfDefinitionWriter wfDefinitionKey(final byte[] bytes)
    {
        typeKeyBuffer.wrap(bytes);
        return this;
    }

    public WfDefinitionWriter resource(final DirectBuffer buffer, final int offset, final int length)
    {
        resourceBuffer.wrap(buffer, offset, length);
        return this;
    }
}
