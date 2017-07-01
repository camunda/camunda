package io.zeebe.broker.transport.controlmessage;

import java.util.Objects;

import org.agrona.MutableDirectBuffer;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.transport.clientapi.ResponseWriter;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferWriter;

public class ControlMessageResponseWriter implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ControlMessageResponseEncoder responseEncoder = new ControlMessageResponseEncoder();

    protected final ResponseWriter responseWriter;

    protected BufferWriter dataWriter;
    protected BrokerEventMetadata metadata;

    public ControlMessageResponseWriter(Dispatcher sendBuffer)
    {
        this.responseWriter = new ResponseWriter(sendBuffer);
    }

    public ControlMessageResponseWriter brokerEventMetadata(BrokerEventMetadata metadata)
    {
        this.metadata = metadata;
        return this;
    }

    public ControlMessageResponseWriter dataWriter(BufferWriter writer)
    {
        this.dataWriter = writer;
        return this;
    }

    public boolean tryWriteResponse()
    {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(dataWriter);

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
            .wrap(buffer, offset);

        final int dataLength = dataWriter.getLength();
        buffer.putShort(offset, (short) dataLength, Protocol.ENDIANNESS);

        offset += ControlMessageResponseEncoder.dataHeaderLength();
        dataWriter.write(buffer, offset);
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ControlMessageResponseEncoder.BLOCK_LENGTH +
                ControlMessageResponseEncoder.dataHeaderLength() +
                dataWriter.getLength();
    }

    protected void reset()
    {
        dataWriter = null;
        metadata = null;
    }

}
