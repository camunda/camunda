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

import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;

public class EventMetadataImpl implements EventMetadata
{

    protected String topicName;
    protected int partitionId = ExecuteCommandRequestEncoder.partitionIdNullValue();
    protected long key = ExecuteCommandRequestEncoder.keyNullValue();
    protected long position = ExecuteCommandRequestEncoder.positionNullValue();
    protected TopicEventType eventType;

    @Override
    public String getTopicName()
    {
        return topicName;
    }

    public void setTopicName(String topicName)
    {
        this.topicName = topicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    public void setPartitionId(int partitionId)
    {
        this.partitionId = partitionId;
    }

    public boolean hasPartitionId()
    {
        return partitionId != ExecuteCommandRequestEncoder.partitionIdNullValue();
    }

    @Override
    public long getPosition()
    {
        return position;
    }

    public void setEventPosition(long position)
    {
        this.position = position;
    }

    @Override
    public long getKey()
    {
        return key;
    }

    public void setEventKey(long key)
    {
        this.key = key;
    }

    @Override
    public TopicEventType getType()
    {
        return eventType;
    }

    public void setEventType(TopicEventType eventType)
    {
        this.eventType = eventType;
    }

    @Override
    public String toString()
    {
        return "EventMetadata [topicName=" + topicName + ", partitionId=" + partitionId + ", key=" +
                key + ", position=" + position + ", eventType=" + eventType + "]";
    }

}
