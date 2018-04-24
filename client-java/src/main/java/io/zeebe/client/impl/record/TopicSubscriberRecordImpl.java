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

import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.event.TopicSubscriberEventImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;

public abstract class TopicSubscriberRecordImpl extends RecordImpl
{
    private String name;
    private long startPosition = -1L;
    private int prefetchCapacity = -1;
    private boolean forceStart;

    public TopicSubscriberRecordImpl(ZeebeObjectMapper objectMapper, RecordType recordType)
    {
        super(objectMapper, recordType, ValueType.SUBSCRIBER);
    }

    public long getStartPosition()
    {
        return startPosition;
    }

    public void setStartPosition(long startPosition)
    {
        this.startPosition = startPosition;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void setPrefetchCapacity(int prefetchCapacity)
    {
        this.prefetchCapacity = prefetchCapacity;
    }

    public int getPrefetchCapacity()
    {
        return prefetchCapacity;
    }

    public boolean isForceStart()
    {
        return forceStart;
    }

    public void setForceStart(boolean forceStart)
    {
        this.forceStart = forceStart;
    }

    @Override
    public Class<? extends RecordImpl> getEventClass()
    {
        return TopicSubscriberEventImpl.class;
    }

}
