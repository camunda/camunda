package org.camunda.tngp.broker.log;

import org.agrona.MutableDirectBuffer;
import org.agrona.sbe.MessageEncoderFlyweight;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.protocol.log.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

public abstract class LogEntryWriter<S extends LogEntryWriter<S, T>, T extends MessageEncoderFlyweight> implements BufferWriter
{

    protected short source;
    protected int sourceEventLogId = MessageHeaderEncoder.sourceEventLogIdNullValue();
    protected long sourceEventPosition = MessageHeaderEncoder.sourceEventPositionNullValue();

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected T bodyEncoder;

    public LogEntryWriter(T bodyEncoder)
    {
        this.bodyEncoder = bodyEncoder;
    }

    protected void writeHeader(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            .resourceId(0)
            .shardId(0)
            .source(source)
            .sourceEventPosition(sourceEventPosition)
            .sourceEventLogId(sourceEventLogId);
    }

    @SuppressWarnings("unchecked")
    public S source(EventSource source)
    {
        this.source = source.value();
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public S sourceEventLogId(int sourceEventLogId)
    {
        this.sourceEventLogId = sourceEventLogId;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public S sourceEventPosition(long sourceEventPosition)
    {
        this.sourceEventPosition = sourceEventPosition;
        return (S) this;
    }

    protected abstract void writeBody(MutableDirectBuffer buffer, int offset);

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        writeHeader(buffer, offset);
        offset += headerEncoder.encodedLength();

        writeBody(buffer, offset);
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH + getBodyLength();
    }

    protected abstract int getBodyLength();


}
