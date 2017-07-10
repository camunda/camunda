/**
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
import io.zeebe.client.event.TopicEvent;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.task.impl.subscription.MsgPackField;

public class TopicEventImpl implements TopicEvent, EventMetadata
{

    protected final String topicName;
    protected final int partitionId;
    protected final long key;
    protected final long position;
    protected final TopicEventType eventType;
    protected final MsgPackField content;

    public TopicEventImpl(final String topicName, final int partitionId, final long key, final long position, final TopicEventType eventType, final byte[] rawContent)
    {
        this.topicName = topicName;
        this.partitionId = partitionId;
        this.position = position;
        this.key = key;
        this.eventType = eventType;
        this.content = new MsgPackField();
        this.content.setMsgPack(rawContent);
    }

    public String getTopicName()
    {
        return topicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public long getEventKey()
    {
        return key;
    }

    @Override
    public long getEventPosition()
    {
        return position;
    }

    @Override
    public String getJson()
    {
        return content.getAsJson();
    }

    public byte[] getAsMsgPack()
    {
        return content.getMsgPack();
    }

    @Override
    public TopicEventType getEventType()
    {
        return eventType;
    }

    @Override
    public String toString()
    {
        return "TopicEventImpl [topicName=" + topicName + ", partitionId=" + partitionId + ", key=" +
                key + ", position=" + position + ", eventType=" + eventType + ", content=" + content.getAsJson() + "]";
    }

}
