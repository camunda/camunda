package org.camunda.tngp.broker.wf.repository.log;

import org.camunda.tngp.dispatcher.FragmentWriter;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfTypeEncoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfTypeWriter implements FragmentWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final WfTypeEncoder encoder = new WfTypeEncoder();

    protected int resourceId;
    protected int shardId;

    protected long id;
    protected int version;
    protected long prevVersionPosition;

    protected UnsafeBuffer typeKeyBuffer = new UnsafeBuffer(0,0);
    protected UnsafeBuffer resourceBuffer = new UnsafeBuffer(0,0);

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH
               + WfTypeEncoder.BLOCK_LENGTH
               + WfTypeEncoder.typeKeyHeaderLength()
               + typeKeyBuffer.capacity()
               + WfTypeEncoder.resourceHeaderLength()
               + resourceBuffer.capacity();
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
            .version(version)
            .prevVersionPosition(prevVersionPosition)
            .putTypeKey(typeKeyBuffer, 0, typeKeyBuffer.capacity())
            .putResource(resourceBuffer, 0, resourceBuffer.capacity());

        typeKeyBuffer.wrap(0,0);
        resourceBuffer.wrap(0,0);
    }

    public WfTypeWriter resourceId(final int value)
    {
        this.resourceId = value;
        return this;
    }

    public WfTypeWriter shardId(final int value)
    {
        this.shardId = value;
        return this;
    }

    public WfTypeWriter id(final long value)
    {
        this.id = value;
        return this;
    }

    public WfTypeWriter version(final int value)
    {
        this.version = value;
        return this;
    }

    public WfTypeWriter prevVersionPosition(long value)
    {
        if(value < 0)
        {
            value = WfTypeEncoder.prevVersionPositionNullValue();
        }

        prevVersionPosition = value;
        return this;
    }

    public WfTypeWriter wfTypeKey(final byte[] bytes)
    {
        typeKeyBuffer.wrap(bytes);
        return this;
    }

    public WfTypeWriter resource(final DirectBuffer buffer, final int offset, final int length)
    {
        resourceBuffer.wrap(buffer, offset, length);
        return this;
    }
}
