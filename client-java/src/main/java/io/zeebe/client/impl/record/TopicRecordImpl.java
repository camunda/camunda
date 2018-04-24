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

import io.zeebe.client.api.record.TopicRecord;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.event.TopicEventImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;

public abstract class TopicRecordImpl extends RecordImpl implements TopicRecord
{
    private String name;
    private int partitions;
    private int replicationFactor;

    public TopicRecordImpl(ZeebeObjectMapper objectMapper, RecordType recordType)
    {
        super(objectMapper, recordType, ValueType.TOPIC);
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public int getPartitions()
    {
        return partitions;
    }

    @Override
    public int getReplicationFactor()
    {
        return replicationFactor;
    }

    @Override
    public Class<? extends RecordImpl> getEventClass()
    {
        return TopicEventImpl.class;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setPartitions(int partitions)
    {
        this.partitions = partitions;
    }

    public void setReplicationFactor(int replicationFactor)
    {
        this.replicationFactor = replicationFactor;
    }
}
