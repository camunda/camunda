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
package io.zeebe.client.impl.cmd;

import static io.zeebe.protocol.clientapi.EventType.NULL_VAL;
import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.commandHeaderLength;
import static io.zeebe.util.VarDataUtil.readBytes;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.protocol.clientapi.*;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.io.ExpandableDirectBufferOutputStream;

public abstract class AbstractExecuteCmdImpl<E, R> extends AbstractCmdImpl<R> implements ClientResponseHandler<R>
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestEncoder commandRequestEncoder = new ExecuteCommandRequestEncoder();

    protected final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    protected final ObjectMapper objectMapper;
    protected final Class<E> eventType;
    protected final EventType commandEventType;

    public AbstractExecuteCmdImpl(
        final ClientCommandManager commandManager,
        final ObjectMapper objectMapper,
        final Topic topic,
        final Class<E> eventType,
        final EventType commandEventType)
    {
        super(commandManager, topic);

        if (commandEventType == null || commandEventType == NULL_VAL)
        {
            throw new IllegalArgumentException("commandEventType cannot be null");
        }

        this.objectMapper = objectMapper;
        this.eventType = eventType;
        this.commandEventType = commandEventType;
    }

    @Override
    public int writeCommand(ExpandableArrayBuffer buffer)
    {
        validate();

        int offset = 0;

        headerEncoder.wrap(buffer, offset)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(commandRequestEncoder.sbeTemplateId())
            .version(commandRequestEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        commandRequestEncoder.wrap(buffer, offset);

        long key = getKey();
        if (key < 0)
        {
            key = ExecuteCommandRequestEncoder.keyNullValue();
        }

        commandRequestEncoder
            .partitionId(topic.getPartitionId())
            .key(key)
            .eventType(commandEventType)
            .topicName(topic.getTopicName());

        offset = commandRequestEncoder.limit();
        final int serializedCommandOffset = offset + commandHeaderLength();

        final ExpandableDirectBufferOutputStream out = new ExpandableDirectBufferOutputStream(buffer, serializedCommandOffset);
        try
        {
            objectMapper.writeValue(out, writeCommand());
        }
        catch (final Throwable e)
        {
            throw new RuntimeException("Failed to serialize command", e);
        }

        buffer.putShort(offset, (short)out.position(), java.nio.ByteOrder.LITTLE_ENDIAN);

        return serializedCommandOffset + out.position();
    }

    protected abstract Object writeCommand();

    protected abstract long getKey();

    protected abstract void reset();

    @Override
    public ClientResponseHandler<R> getResponseHandler()
    {
        return this;
    }

    @Override
    public int getResponseSchemaId()
    {
        return responseDecoder.sbeSchemaId();
    }

    @Override
    public int getResponseTemplateId()
    {
        return responseDecoder.sbeTemplateId();
    }

    @Override
    public R readResponse(final DirectBuffer responseBuffer, final int offset, final int blockLength, final int version)
    {
        responseDecoder.wrap(responseBuffer, offset, blockLength, version);

        final long key = responseDecoder.key();

        // skip topic name
        responseDecoder.topicName();

        final byte[] eventBuffer = readBytes(responseDecoder::getEvent, responseDecoder::eventLength);

        final E event = readEvent(eventBuffer);

        return getResponseValue(key, event);
    }

    protected E readEvent(final byte[] buffer)
    {
        try
        {
            return objectMapper.readValue(buffer, eventType);
        }
        catch (final IOException e)
        {
            throw new RuntimeException("Failed to deserialize command", e);
        }
    }

    public void validate()
    {
        topic.validate();
    }

    protected abstract R getResponseValue(long key, E event);

}
