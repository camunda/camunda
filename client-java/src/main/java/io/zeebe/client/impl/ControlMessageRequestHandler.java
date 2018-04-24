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

import io.zeebe.protocol.clientapi.*;
import org.agrona.*;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.ExpandableDirectBufferOutputStream;

@SuppressWarnings("rawtypes")
public class ControlMessageRequestHandler implements RequestResponseHandler
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ControlMessageRequestEncoder encoder = new ControlMessageRequestEncoder();

    protected final ControlMessageRequestDecoder decoder = new ControlMessageRequestDecoder();

    protected final ZeebeObjectMapperImpl objectMapper;

    protected ExpandableArrayBuffer serializedMessage = new ExpandableArrayBuffer();
    protected int serializedMessageLength = 0;

    protected ControlMessageRequest message;

    public ControlMessageRequestHandler(ZeebeObjectMapperImpl objectMapper, ControlMessageRequest controlMessage)
    {
        this.objectMapper = objectMapper;
        this.message = controlMessage;
        serialize(controlMessage);
    }

    protected void serialize(ControlMessageRequest message)
    {
        int offset = 0;
        headerEncoder.wrap(serializedMessage, offset)
            .blockLength(encoder.sbeBlockLength())
            .schemaId(encoder.sbeSchemaId())
            .templateId(encoder.sbeTemplateId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(serializedMessage, offset);

        encoder.messageType(message.getType());
        encoder.partitionId(message.getTargetPartition());

        offset = encoder.limit();
        final int messageHeaderOffset = offset;
        final int serializedMessageOffset = messageHeaderOffset + ControlMessageRequestEncoder.dataHeaderLength();

        final ExpandableDirectBufferOutputStream out = new ExpandableDirectBufferOutputStream(serializedMessage, serializedMessageOffset);

        objectMapper.toJson(out, message.getRequest());

        // can only write the header after we have written the message, as we don't know the length beforehand
        final short commandLength = (short)out.position();
        serializedMessage.putShort(messageHeaderOffset, commandLength, java.nio.ByteOrder.LITTLE_ENDIAN);

        serializedMessageLength = serializedMessageOffset + out.position();
    }

    @Override
    public int getLength()
    {
        return serializedMessageLength;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        buffer.putBytes(offset, serializedMessage, 0, serializedMessageLength);
    }

    @Override
    public boolean handlesResponse(MessageHeaderDecoder responseHeader)
    {
        return responseHeader.schemaId() == ControlMessageResponseDecoder.SCHEMA_ID && responseHeader.templateId() == ControlMessageResponseDecoder.TEMPLATE_ID;
    }

    @SuppressWarnings({ "unchecked" })
    @Override
    public Object getResult(DirectBuffer buffer, int offset, int blockLength, int version)
    {
        decoder.wrap(buffer, offset, blockLength, version);

        final int dataLength = decoder.dataLength();
        final DirectBufferInputStream inStream = new DirectBufferInputStream(
                buffer,
                decoder.limit() + ControlMessageRequestDecoder.dataHeaderLength(),
                dataLength);

        final Object response = objectMapper.fromJson(inStream, message.getResponseClass());

        message.onResponse(response);

        return response;
    }

    @Override
    public int getTargetPartition()
    {
        return message.getTargetPartition();
    }

    @Override
    public void onSelectedPartition(int partitionId)
    {
        message.setTargetPartition(partitionId);
        encoder.partitionId(partitionId);
    }

    @Override
    public String getTargetTopic()
    {
        return message.getTargetTopic();
    }

    @Override
    public String describeRequest()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("[ target topic = ");
        sb.append(message.getTargetTopic());
        sb.append(", target partition = ");
        if (message.getTargetPartition() >= 0)
        {
            sb.append(message.getTargetPartition());
        }
        else
        {
            sb.append("unspecified");
        }
        sb.append(", type = ");
        sb.append(message.getType().name());
        sb.append("]");

        return sb.toString();
    }

}
