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
package io.zeebe.protocol.impl;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.RecordMetadataDecoder;
import io.zeebe.protocol.clientapi.RecordMetadataEncoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class RecordMetadata implements BufferWriter, BufferReader
{
    public static final int ENCODED_LENGTH = MessageHeaderEncoder.ENCODED_LENGTH +
            RecordMetadataEncoder.BLOCK_LENGTH;

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    protected RecordMetadataEncoder encoder = new RecordMetadataEncoder();
    protected RecordMetadataDecoder decoder = new RecordMetadataDecoder();

    private RecordType recordType = RecordType.NULL_VAL;
    private short intentValue = Intent.NULL_VAL;
    private Intent intent = null;
    protected int requestStreamId;
    protected long requestId;
    protected long subscriberKey;
    protected int protocolVersion = Protocol.PROTOCOL_VERSION; // always the current version by default
    protected ValueType valueType = ValueType.NULL_VAL;
    protected long incidentKey;

    public RecordMetadata()
    {
        reset();
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        reset();

        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        recordType = decoder.recordType();
        requestStreamId = decoder.requestStreamId();
        requestId = decoder.requestId();
        subscriberKey = decoder.subscriptionId();
        protocolVersion = decoder.protocolVersion();
        valueType = decoder.valueType();
        intent = Intent.fromProtocolValue(valueType, decoder.intent());
        incidentKey = decoder.incidentKey();
    }

    @Override
    public int getLength()
    {
        return ENCODED_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset);

        headerEncoder.blockLength(encoder.sbeBlockLength())
            .templateId(encoder.sbeTemplateId())
            .schemaId(encoder.sbeSchemaId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(buffer, offset);

        encoder
            .recordType(recordType)
            .requestStreamId(requestStreamId)
            .requestId(requestId)
            .subscriptionId(subscriberKey)
            .protocolVersion(protocolVersion)
            .valueType(valueType)
            .intent(intentValue)
            .incidentKey(incidentKey);
    }

    public long getRequestId()
    {
        return requestId;
    }

    public RecordMetadata requestId(long requestId)
    {
        this.requestId = requestId;
        return this;
    }

    public int getRequestStreamId()
    {
        return requestStreamId;
    }

    public RecordMetadata requestStreamId(int requestStreamId)
    {
        this.requestStreamId = requestStreamId;
        return this;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    public RecordMetadata subscriberKey(long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
        return this;
    }

    public RecordMetadata protocolVersion(int protocolVersion)
    {
        this.protocolVersion = protocolVersion;
        return this;
    }

    public int getProtocolVersion()
    {
        return protocolVersion;
    }

    public ValueType getValueType()
    {
        return valueType;
    }

    public RecordMetadata valueType(ValueType eventType)
    {
        this.valueType = eventType;
        return this;
    }

    public long getIncidentKey()
    {
        return incidentKey;
    }

    public RecordMetadata incidentKey(long incidentKey)
    {
        this.incidentKey = incidentKey;
        return this;
    }

    public boolean hasIncidentKey()
    {
        return incidentKey != RecordMetadataDecoder.incidentKeyNullValue();
    }

    public RecordMetadata intent(Intent intent)
    {
        this.intent = intent;
        this.intentValue = intent.value();
        return this;
    }

    public RecordMetadata intent(short intent)
    {
        this.intentValue = intent;
        return this;
    }

    public Intent getIntent()
    {
        return intent;
    }

    public RecordMetadata recordType(RecordType recordType)
    {
        this.recordType = recordType;
        return this;
    }

    public RecordType getRecordType()
    {
        return recordType;
    }

    public RecordMetadata reset()
    {
        recordType = RecordType.NULL_VAL;
        requestId = RecordMetadataEncoder.requestIdNullValue();
        requestStreamId = RecordMetadataEncoder.requestStreamIdNullValue();
        subscriberKey = RecordMetadataEncoder.subscriptionIdNullValue();
        protocolVersion = Protocol.PROTOCOL_VERSION;
        valueType = ValueType.NULL_VAL;
        incidentKey = RecordMetadataEncoder.incidentKeyNullValue();
        intentValue = Intent.NULL_VAL;
        intent = null;
        return this;
    }

    public boolean hasRequestMetadata()
    {
        return requestId != RecordMetadataEncoder.requestIdNullValue() &&
                requestStreamId != RecordMetadataEncoder.requestStreamIdNullValue();
    }

    public void copyRequestMetadata(RecordMetadata target)
    {
        target.requestId(requestId).requestStreamId(requestStreamId);
    }

    @Override
    public String toString()
    {
        return "BrokerEventMetadata{" + "requestStreamId=" + requestStreamId + ", requestId=" + requestId +
            ", subscriberKey=" + subscriberKey + ", protocolVersion=" + protocolVersion + ", eventType=" + valueType +
            ", incidentKey=" + incidentKey + '}';
    }
}
