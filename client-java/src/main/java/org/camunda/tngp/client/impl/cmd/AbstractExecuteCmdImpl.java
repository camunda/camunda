/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.impl.cmd;

import static org.camunda.tngp.protocol.clientapi.EventType.NULL_VAL;

import java.io.IOException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestEncoder;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.buffer.RequestWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractExecuteCmdImpl<E, R> extends AbstractCmdImpl<R> implements RequestWriter, ClientResponseHandler<R>
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestEncoder commandRequestEncoder = new ExecuteCommandRequestEncoder();

    protected final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    protected final ObjectMapper objectMapper;
    protected final Class<E> eventType;
    protected final EventType commandEventType;
    protected final int topicId;

    protected byte[] serializedCommand;

    public AbstractExecuteCmdImpl(
            ClientCmdExecutor cmdExecutor,
            ObjectMapper objectMapper,
            Class<E> eventType,
            int topicId,
            EventType commandEventType)
    {
        super(cmdExecutor);

        if (commandEventType == null || commandEventType == NULL_VAL)
        {
            throw new IllegalArgumentException("commandEventType cannot be null");
        }

        this.objectMapper = objectMapper;
        this.eventType = eventType;
        this.commandEventType = commandEventType;
        this.topicId = topicId;
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return this;
    }

    @Override
    public int getLength()
    {
        ensureCommandInitialized();

        return TransportHeaderDescriptor.headerLength() +
                RequestResponseProtocolHeaderDescriptor.headerLength() +
                headerEncoder.encodedLength() +
                commandRequestEncoder.sbeBlockLength() +
                serializedCommand.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        ensureCommandInitialized();

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
            key = ExecuteCommandRequestEncoder.longKeyNullValue();
        }

        commandRequestEncoder
            .topicId(topicId)
            .longKey(key)
            .eventType(commandEventType)
            .putCommand(serializedCommand, 0, serializedCommand.length);

        serializedCommand = null;
        reset();
    }

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
    public R readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        responseDecoder.wrap(responseBuffer, offset, responseDecoder.sbeBlockLength(), responseDecoder.sbeSchemaVersion());

        final long key = responseDecoder.longKey();

        // must read byte key - however, it's currently not used
        responseDecoder.bytesKey();

        final byte[] eventBuffer = new byte[responseDecoder.eventLength()];
        responseDecoder.getEvent(eventBuffer, 0, eventBuffer.length);

        final E event = readEvent(eventBuffer);

        return getResponseValue(key, event);
    }

    protected E readEvent(byte[] buffer)
    {
        try
        {
            return objectMapper.readValue(buffer, eventType);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to deserialize command", e);
        }
    }

    protected abstract R getResponseValue(long key, E event);

}
