package io.zeebe.broker.transport.clientapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.eventHeaderLength;
import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.keyNullValue;
import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.partitionIdNullValue;
import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.topicNameHeaderLength;

import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.buffer.BufferWriter;

public class CommandResponseWriter implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected long key = keyNullValue();

    protected BufferWriter eventWriter;
    protected final ServerResponse response = new ServerResponse();
    protected final ServerOutput output;

    public CommandResponseWriter(final ServerOutput output)
    {
        this.output = output;
    }

    public CommandResponseWriter topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public CommandResponseWriter partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public CommandResponseWriter key(final long key)
    {
        this.key = key;
        return this;
    }

    public CommandResponseWriter eventWriter(final BufferWriter writer)
    {
        this.eventWriter = writer;
        return this;
    }

    public boolean tryWriteResponse(int remoteStreamId, long requestId)
    {
        Objects.requireNonNull(eventWriter);

        try
        {
            response.reset()
                .remoteStreamId(remoteStreamId)
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
    public void write(final MutableDirectBuffer buffer, int offset)
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
            .putTopicName(topicName, 0, topicName.capacity())
            .partitionId(partitionId)
            .key(key);

        offset = responseEncoder.limit();

        final int eventLength = eventWriter.getLength();
        buffer.putShort(offset, (short) eventLength, Protocol.ENDIANNESS);

        offset += eventHeaderLength();
        eventWriter.write(buffer, offset);
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ExecuteCommandResponseEncoder.BLOCK_LENGTH +
                topicNameHeaderLength() +
                topicName.capacity() +
                eventHeaderLength() +
                eventWriter.getLength();
    }

    protected void reset()
    {
        topicName.wrap(0, 0);
        partitionId = partitionIdNullValue();
        key = keyNullValue();
        eventWriter = null;
    }

}
