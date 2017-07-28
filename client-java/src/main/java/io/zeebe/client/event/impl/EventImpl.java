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
package io.zeebe.client.event.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.zeebe.client.event.Event;
import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.TopicEventType;

public abstract class EventImpl implements Event
{
    protected final EventMetadataImpl metadata = new EventMetadataImpl();
    protected final String state;

    public EventImpl(TopicEventType type, String state)
    {
        this.metadata.setEventType(type);
        this.state = state;
    }

    public EventImpl(EventImpl baseEvent, String state)
    {
        updateMetadata(baseEvent.metadata);
        this.state = state;
    }

    @Override
    @JsonIgnore
    public EventMetadata getMetadata()
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

    public void setKey(long key)
    {
        this.metadata.setEventKey(key);
    }

    public void setEventPosition(long position)
    {
        this.metadata.setEventPosition(position);
    }

    public boolean hasValidPartitionId()
    {
        return this.metadata.hasPartitionId();
    }

    public void updateMetadata(EventMetadata other)
    {
        this.metadata.setEventKey(other.getKey());
        this.metadata.setEventPosition(other.getPosition());
        this.metadata.setEventType(other.getType());
        this.metadata.setPartitionId(other.getPartitionId());
        this.metadata.setTopicName(other.getTopicName());
    }

    @Override
    public String getState()
    {
        return state;
    }

}
