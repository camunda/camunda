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

import io.zeebe.client.api.record.TopicSubscriptionRecord;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.event.TopicSubscriptionEventImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;

public abstract class TopicSubscriptionRecordImpl extends RecordImpl implements TopicSubscriptionRecord
{
    private String name;
    private long ackPosition = -1L;

    public TopicSubscriptionRecordImpl(ZeebeObjectMapper objectMapper, RecordType recordType)
    {
        super(objectMapper, recordType, ValueType.SUBSCRIPTION);
    }

    @Override
    public String getName()
    {
        return name;
    }

    public void setName(String subscriptionName)
    {
        this.name = subscriptionName;
    }

    @Override
    public long getAckPosition()
    {
        return ackPosition;
    }

    public void setAckPosition(long ackPosition)
    {
        this.ackPosition = ackPosition;
    }

    @Override
    public Class<? extends RecordImpl> getEventClass()
    {
        return TopicSubscriptionEventImpl.class;
    }

}
