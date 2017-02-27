package org.camunda.tngp.broker.transport.clientapi;

import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.Protocol;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

public class CommandResponseWriter implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    protected int topicId;
    protected long longKey;

    protected final UnsafeBuffer bytesKey = new UnsafeBuffer(0, 0);

    protected BufferWriter eventWriter;
    protected BrokerEventMetadata metadata;
    protected final ResponseWriter responseWriter;

    public CommandResponseWriter(Dispatcher sendBuffer)
    {
        this.responseWriter = new ResponseWriter(sendBuffer);
    }

    public CommandResponseWriter topicId(int topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public CommandResponseWriter longKey(long key)
    {
        this.longKey = key;
        return this;
    }

    public CommandResponseWriter bytesKey(DirectBuffer buffer)
    {
        bytesKey.wrap(buffer, 0, buffer.capacity());
        return this;
    }

    public CommandResponseWriter brokerEventMetadata(BrokerEventMetadata metadata)
    {
        this.metadata = metadata;
        return this;
    }

    public CommandResponseWriter eventWriter(BufferWriter writer)
    {
        this.eventWriter = writer;
        return this;
    }

    public boolean tryWriteResponse()
    {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(eventWriter);

        try
        {
            return responseWriter.tryWrite(
                    metadata.getReqChannelId(),
                    metadata.getReqConnectionId(),
                    metadata.getReqRequestId(),
                    this);
        }
        finally
        {
            reset();
        }
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        // protocol header
        messageHeaderEncoder
            .wrap(buffer, offset)
            .blockLength(responseEncoder.sbeBlockLength())
            .templateId(responseEncoder.sbeTemplateId())
            .schemaId(responseEncoder.sbeSchemaId())
            .version(responseEncoder.sbeSchemaVersion());

        offset += messageHeaderEncoder.encodedLength();

        // protocol message
        responseEncoder
            .wrap(buffer, offset)
            .topicId(topicId)
            .longKey(longKey)
            .putBytesKey(bytesKey, 0, bytesKey.capacity());

        offset += ExecuteCommandResponseEncoder.BLOCK_LENGTH;
        offset += ExecuteCommandResponseEncoder.bytesKeyHeaderLength();
        offset += bytesKey.capacity();

        final int eventLength = eventWriter.getLength();
        buffer.putShort(offset, (short) eventLength, Protocol.ENDIANNESS);

        offset += ExecuteCommandResponseEncoder.eventHeaderLength();
        eventWriter.write(buffer, offset);
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ExecuteCommandResponseEncoder.BLOCK_LENGTH +
                ExecuteCommandResponseEncoder.bytesKeyHeaderLength() +
                bytesKey.capacity() +
                ExecuteCommandResponseEncoder.eventHeaderLength() +
                eventWriter.getLength();
    }

    protected void reset()
    {
        topicId = ExecuteCommandResponseEncoder.topicIdNullValue();
        longKey = ExecuteCommandResponseEncoder.longKeyNullValue();
        bytesKey.wrap(0, 0);
        eventWriter = null;
        metadata = null;
    }

}
