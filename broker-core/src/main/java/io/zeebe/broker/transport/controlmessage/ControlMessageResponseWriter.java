package io.zeebe.broker.transport.controlmessage;

import java.util.Objects;

import org.agrona.MutableDirectBuffer;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.buffer.BufferWriter;

public class ControlMessageResponseWriter implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ControlMessageResponseEncoder responseEncoder = new ControlMessageResponseEncoder();

    protected BufferWriter dataWriter;

    protected final ServerOutput output;
    protected final ServerResponse response = new ServerResponse();

    public ControlMessageResponseWriter(ServerOutput output)
    {
        this.output = output;
    }

    public ControlMessageResponseWriter dataWriter(BufferWriter writer)
    {
        this.dataWriter = writer;
        return this;
    }

    public boolean tryWriteResponse(int requestStreamId, long requestId)
    {
        Objects.requireNonNull(dataWriter);

        try
        {
            response.reset()
                .remoteStreamId(requestStreamId)
                .requestId(requestId)
                .writer(this);

            return output.sendResponse(response);
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
    }

}
