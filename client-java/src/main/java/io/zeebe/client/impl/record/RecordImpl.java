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
package io.zeebe.client.impl.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;

public abstract class RecordImpl implements Record
{
    private final RecordMetadataImpl metadata = new RecordMetadataImpl();
    private final ZeebeObjectMapper objectMapper;

    public RecordImpl(
            ZeebeObjectMapper objectMapper,
            RecordType recordType,
            ValueType valueType)
    {
        this.metadata.setRecordType(recordType);
        this.metadata.setValueType(valueType);
        this.objectMapper = objectMapper;
    }

    public RecordImpl(RecordImpl baseEvent, Intent intent)
    {
        updateMetadata(baseEvent.metadata);
        setIntent(intent);

        this.objectMapper = baseEvent.objectMapper;
    }

    @Override
    @JsonIgnore
    public RecordMetadataImpl getMetadata()
    {
        return metadata;
    }

    public void setTopicName(String name)
    {
        this.metadata.setTopicName(name);
    }

    public void setPartitionId(int id)
    {
        this.metadata.setPartitionId(id);
    }

    @Override
    @JsonIgnore
    public long getKey()
    {
        return metadata.getKey();
    }

    public void setKey(long key)
    {
        this.metadata.setKey(key);
    }

    public void setPosition(long position)
    {
        this.metadata.setPosition(position);
    }

    public boolean hasValidPartitionId()
    {
        return this.metadata.hasPartitionId();
    }

    public void updateMetadata(RecordMetadataImpl other)
    {
        this.metadata.setKey(other.getKey());
        this.metadata.setPosition(other.getPosition());
        this.metadata.setTopicName(other.getTopicName());
        this.metadata.setPartitionId(other.getPartitionId());
        this.metadata.setRecordType(other.getProtocolRecordType());
        this.metadata.setValueType(other.getProtocolValueType());
        this.metadata.setIntent(other.getProtocolIntent());
    }

    @Override
    public String toJson()
    {
        return objectMapper.toJson(this);
    }

    public void setIntent(Intent intent)
    {
        this.metadata.setIntent(intent);
    }

    @JsonIgnore
    public abstract Class<? extends RecordImpl> getEventClass();

}
