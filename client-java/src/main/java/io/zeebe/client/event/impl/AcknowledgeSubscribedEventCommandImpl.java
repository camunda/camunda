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

import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;

public class AcknowledgeSubscribedEventCommandImpl extends CommandImpl<TopicSubscriptionEvent>
{
    protected final TopicSubscriptionEvent ack = new TopicSubscriptionEvent(SubscriptionEventType.ACKNOWLEDGE.name());

    public AcknowledgeSubscribedEventCommandImpl(final RequestManager commandManager, String topicName, int partitionId)
    {
        super(commandManager);
        ack.setTopicName(topicName);
        ack.setPartitionId(partitionId);
    }

    public AcknowledgeSubscribedEventCommandImpl subscriptionName(String subscriptionName)
    {
        ack.setName(subscriptionName);
        return this;
    }

    public AcknowledgeSubscribedEventCommandImpl ackPosition(long position)
    {
        this.ack.setAckPosition(position);
        return this;
    }

    @Override
    public EventImpl getEvent()
    {
        return ack;
    }

    @Override
    public String getExpectedStatus()
    {
        return SubscriptionEventType.ACKNOWLEDGED.name();
    }

}
