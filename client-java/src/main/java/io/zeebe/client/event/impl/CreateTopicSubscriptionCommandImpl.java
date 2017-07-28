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

public class CreateTopicSubscriptionCommandImpl extends CommandImpl<TopicSubscriberEvent>
{
    protected final TopicSubscriberEvent subscription = new TopicSubscriberEvent(SubscriberEventType.SUBSCRIBE.name());

    public CreateTopicSubscriptionCommandImpl(final RequestManager commandManager, final String topicName, final int partitionId)
    {
        super(commandManager);
        this.subscription.setTopicName(topicName);
        this.subscription.setPartitionId(partitionId);
    }

    public CreateTopicSubscriptionCommandImpl startPosition(long startPosition)
    {
        this.subscription.setStartPosition(startPosition);
        return this;
    }

    public CreateTopicSubscriptionCommandImpl name(String name)
    {
        this.subscription.setName(name);
        return this;
    }

    public CreateTopicSubscriptionCommandImpl prefetchCapacity(int prefetchCapacity)
    {
        this.subscription.setPrefetchCapacity(prefetchCapacity);
        return this;
    }

    public CreateTopicSubscriptionCommandImpl forceStart(boolean forceStart)
    {
        this.subscription.setForceStart(forceStart);
        return this;
    }

    @Override
    public EventImpl getEvent()
    {
        return subscription;
    }

    @Override
    public String getExpectedStatus()
    {
        return SubscriberEventType.SUBSCRIBED.name();
    }

}
