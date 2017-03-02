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

import static org.camunda.tngp.protocol.clientapi.ControlMessageType.NULL_VAL;

import java.io.IOException;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestEncoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageResponseDecoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.RequestWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractControlMessageCmd<E, R> extends AbstractCmdImpl<R> implements RequestWriter, ClientResponseHandler<R>
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ControlMessageRequestEncoder requestEncoder = new ControlMessageRequestEncoder();
    protected final ControlMessageResponseDecoder responseDecoder = new ControlMessageResponseDecoder();

    protected final ObjectMapper objectMapper;
    protected final Class<E> messageType;
    protected final ControlMessageType controlMessageType;

    protected final Function<E, R> responseHandler;

    protected byte[] serializedCommand;

    public AbstractControlMessageCmd(ClientCmdExecutor cmdExecutor,
            ObjectMapper objectMapper,
            Class<E> messageType,
            ControlMessageType controlMessageType,
            Function<E, R> responseHandler)
    {
        super(cmdExecutor);

        if (controlMessageType == null || controlMessageType == NULL_VAL)
        {
            throw new IllegalArgumentException("control message type cannot be null");
        }

        this.objectMapper = objectMapper;
        this.messageType = messageType;
        this.controlMessageType = controlMessageType;
        this.responseHandler = responseHandler;
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

        return headerEncoder.encodedLength() +
                requestEncoder.sbeBlockLength() +
                ControlMessageRequestEncoder.dataHeaderLength() +
                serializedCommand.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
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

        if (responseHandler != null)
        {
            responseDecoder.wrap(responseBuffer, offset, blockLength, version);

            final byte[] dataBuffer = new byte[responseDecoder.dataLength()];
            responseDecoder.getData(dataBuffer, 0, dataBuffer.length);

            final E data = readData(dataBuffer);

            result = responseHandler.apply(data);
        }

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

}
