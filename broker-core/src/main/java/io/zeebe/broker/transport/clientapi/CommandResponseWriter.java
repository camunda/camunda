/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.transport.clientapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.*;
import static io.zeebe.protocol.clientapi.SubscribedEventEncoder.positionNullValue;

import java.util.Objects;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CommandResponseWriter implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected long position = positionNullValue();
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

    public CommandResponseWriter position(final long position)
    {
        this.position = position;
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
            .position(position)
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
