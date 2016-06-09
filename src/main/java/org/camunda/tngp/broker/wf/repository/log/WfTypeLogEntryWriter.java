package org.camunda.tngp.broker.wf.repository.log;

import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfTypeEncoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfTypeLogEntryWriter
{
    protected final UnsafeBuffer buffer = new UnsafeBuffer(0,0);

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    protected final WfTypeEncoder encoder = new WfTypeEncoder();

    protected final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0,0);
    protected final UnsafeBuffer wfTypeBuffer = new UnsafeBuffer(0,0);

    public WfTypeLogEntryWriter wrap(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(encoder.sbeBlockLength())
            .schemaId(encoder.sbeSchemaId())
            .templateId(encoder.sbeTemplateId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(buffer, offset);

        return this;
    }

    public WfTypeLogEntryWriter resourceId(final int resourceId)
    {
        headerEncoder.resourceId(resourceId);
        return this;
    }

    public WfTypeLogEntryWriter shardId(final int shardId)
    {
        headerEncoder.shardId(shardId);
        return this;
    }

    public WfTypeLogEntryWriter id(final long value)
    {
        encoder.id(value);
        return this;
    }

    public WfTypeLogEntryWriter version(int value)
    {
        encoder.version(value);
        return this;
    }

    public WfTypeLogEntryWriter prevVersionPosition(long value)
    {
        encoder.prevVersionPosition(value);
        return this;
    }

    public WfTypeLogEntryWriter payload(DirectBuffer buffer, int offset, int length)
    {
        payloadBuffer.wrap(buffer, offset, length);
        return this;
    }

    public WfTypeLogEntryWriter wfType(byte[] bytes, int offset, int length)
    {
        wfTypeBuffer.wrap(bytes, offset, length);
        return this;
    }

    public WfTypeLogEntryWriter wfType(DirectBuffer buffer, int offset, int length)
    {
        wfTypeBuffer.wrap(buffer, offset, length);
        return this;
    }

    public WfTypeLogEntryWriter flush()
    {

        encoder.putTypeKey(wfTypeBuffer, 0, wfTypeBuffer.capacity());
        encoder.putResource(payloadBuffer, 0, payloadBuffer.capacity());
        return this;
    }
}
