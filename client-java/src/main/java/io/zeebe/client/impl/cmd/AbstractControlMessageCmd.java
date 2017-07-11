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

import static io.zeebe.protocol.clientapi.ControlMessageType.NULL_VAL;
import static io.zeebe.util.VarDataUtil.readBytes;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.protocol.clientapi.*;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public abstract class AbstractControlMessageCmd<E, R> extends AbstractCmdImpl<R> implements ClientResponseHandler<R>
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ControlMessageRequestEncoder requestEncoder = new ControlMessageRequestEncoder();
    protected final ControlMessageResponseDecoder responseDecoder = new ControlMessageResponseDecoder();

    protected final ObjectMapper objectMapper;
    protected final Class<E> messageType;
    protected final ControlMessageType controlMessageType;

    protected byte[] serializedCommand;

    public AbstractControlMessageCmd(
            ClientCommandManager commandManager,
            ObjectMapper objectMapper,
            Topic topic,
            Class<E> messageType,
            ControlMessageType controlMessageType)
    {
        super(commandManager, topic);

        if (controlMessageType == null || controlMessageType == NULL_VAL)
        {
            throw new IllegalArgumentException("control message type cannot be null");
        }

        this.objectMapper = objectMapper;
        this.messageType = messageType;
        this.controlMessageType = controlMessageType;
    }

    @Override
    public int writeCommand(ExpandableArrayBuffer buffer)
    {
        validate();

        int offset = 0;

        ensureCommandInitialized();

        headerEncoder.wrap(buffer, offset)
            .blockLength(requestEncoder.sbeBlockLength())
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        requestEncoder.wrap(buffer, offset);

        requestEncoder
            .messageType(controlMessageType)
            .putData(serializedCommand, 0, serializedCommand.length);

        serializedCommand = null;
        reset();

        return requestEncoder.limit();
    }

    protected abstract void validate();

    private void ensureCommandInitialized()
    {
        if (serializedCommand == null)
        {
            try
            {
                serializedCommand = objectMapper.writeValueAsBytes(writeCommand());
            }
            catch (JsonProcessingException e)
            {
                throw new RuntimeException("Failed to serialize command", e);
            }
        }
    }

    protected abstract Object writeCommand();

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
    public R readResponse(DirectBuffer responseBuffer, int offset, int blockLength, int version)
    {
        R result = null;

        responseDecoder.wrap(responseBuffer, offset, blockLength, version);

        final byte[] dataBuffer = readBytes(responseDecoder::getData, responseDecoder::dataLength);

        final E data = readData(dataBuffer);

        result = getResponseValue(data);

        return result;
    }

    protected E readData(byte[] buffer)
    {
        try
        {
            return objectMapper.readValue(buffer, messageType);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to deserialize command", e);
        }
    }

    protected abstract R getResponseValue(E data);
}
