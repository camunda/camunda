/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.commandHeaderLength;

import java.util.function.BiFunction;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.ExpandableDirectBufferOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.event.impl.EventTypeMapping;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.transport.RemoteAddress;

public class CommandRequestHandler implements RequestResponseHandler
{
    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected ExecuteCommandRequestEncoder encoder = new ExecuteCommandRequestEncoder();

    protected ExecuteCommandResponseDecoder decoder = new ExecuteCommandResponseDecoder();

    protected EventImpl event;
    protected String expectedState;
    protected BiFunction<EventImpl, EventImpl, String> errorFunction;

    protected final ObjectMapper objectMapper;

    protected ExpandableArrayBuffer serializedCommand = new ExpandableArrayBuffer();
    protected int serializedCommandLength = 0;

    public CommandRequestHandler(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public void configure(CommandImpl command)
    {
        this.event = command.getEvent();
        this.expectedState = command.getExpectedStatus();
        this.errorFunction = command::generateError;
        serialize(event);
    }

    protected void serialize(EventImpl event)
    {
        int offset = 0;
        headerEncoder.wrap(serializedCommand, offset)
            .blockLength(encoder.sbeBlockLength())
            .schemaId(encoder.sbeSchemaId())
            .templateId(encoder.sbeTemplateId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(serializedCommand, offset);

        final EventMetadata metadata = event.getMetadata();

        if (metadata.getKey() < 0)
        {
            encoder.key(ExecuteCommandRequestEncoder.keyNullValue());
        }
        else
        {
            encoder.key(metadata.getKey());
        }

        encoder
            .partitionId(metadata.getPartitionId())
            .eventType(EventTypeMapping.mapEventType(metadata.getType()))
            .topicName(metadata.getTopicName())
            .position(metadata.getPosition());

        offset = encoder.limit();
        final int serializedCommandOffset = offset + commandHeaderLength();

        final ExpandableDirectBufferOutputStream out = new ExpandableDirectBufferOutputStream(serializedCommand, serializedCommandOffset);
        try
        {
            objectMapper.writeValue(out, event);
        }
        catch (final Throwable e)
        {
            throw new RuntimeException("Failed to serialize command", e);
        }

        serializedCommand.putShort(offset, (short)out.position(), java.nio.ByteOrder.LITTLE_ENDIAN);

        serializedCommandLength = serializedCommandOffset + out.position();
    }

    @Override
    public int getLength()
    {
        return serializedCommandLength;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        buffer.putBytes(offset, serializedCommand, 0, serializedCommandLength);
    }

    @Override
    public boolean handlesResponse(MessageHeaderDecoder responseHeader)
    {
        return responseHeader.schemaId() == ExecuteCommandResponseDecoder.SCHEMA_ID && responseHeader.templateId() == ExecuteCommandResponseDecoder.TEMPLATE_ID;
    }

    @Override
    public EventImpl getResult(DirectBuffer buffer, int offset, int blockLength, int version)
    {
        decoder.wrap(buffer, offset, blockLength, version);

        final long key = decoder.key();
        final int partitionId = decoder.partitionId();
        final String topicName = decoder.topicName();
        final long position = decoder.position();

        final int eventLength = decoder.eventLength();

        final DirectBufferInputStream inStream = new DirectBufferInputStream(
                buffer,
                decoder.limit() + ExecuteCommandResponseDecoder.eventHeaderLength(),
                eventLength);
        final EventImpl result;
        try
        {
            result = objectMapper.readValue(inStream, event.getClass());
        }
        catch (Exception e)
        {
            throw new ClientException("Cannot deserialize event in response", e);
        }

        result.setKey(key);
        result.setPartitionId(partitionId);
        result.setTopicName(topicName);
        result.setEventPosition(position);

        if (expectedState != null && !expectedState.equals(result.getState()))
        {
            throw new ClientCommandRejectedException(errorFunction.apply(event, result));
        }

        return result;
    }

    @Override
    public RemoteAddress getTarget(ClientTopologyManager currentTopology)
    {
        final EventMetadata metadata = event.getMetadata();

        return currentTopology.getLeaderForTopic(new Partition(metadata.getTopicName(), metadata.getPartitionId()));
    }

    @Override
    public String describeRequest()
    {
        final EventMetadata eventMetadata = event.getMetadata();
        return "[ topic = " + eventMetadata.getTopicName() +
                ", partition = " + eventMetadata.getPartitionId() +
                ", event type = " + eventMetadata.getType().name() + "]";
    }

}
