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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.protocol.clientapi.EventType;

public class CreateTopicSubscriptionCmdImpl extends AbstractExecuteCmdImpl<TopicSubscriberEvent, EventSubscriptionCreationResult>
{
    protected final TopicSubscriberEvent subscription = new TopicSubscriberEvent();

    public CreateTopicSubscriptionCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TopicSubscriberEvent.class, EventType.SUBSCRIBER_EVENT);
    }

    public CreateTopicSubscriptionCmdImpl startPosition(long startPosition)
    {
        this.subscription.setStartPosition(startPosition);
        return this;
    }

    public CreateTopicSubscriptionCmdImpl name(String name)
    {
        this.subscription.setName(name);
        return this;
    }

    public CreateTopicSubscriptionCmdImpl prefetchCapacity(int prefetchCapacity)
    {
        this.subscription.setPrefetchCapacity(prefetchCapacity);
        return this;
    }

    public CreateTopicSubscriptionCmdImpl forceStart(boolean forceStart)
    {
        this.subscription.setForceStart(forceStart);
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        this.subscription.setEventType(SubscriberEventType.SUBSCRIBE);
        return subscription;
    }

    @Override
    protected void reset()
    {
        subscription.reset();
    }

    @Override
    protected long getKey()
    {
        return -1;
    }

    @Override
    protected EventSubscriptionCreationResult getResponseValue(long key, TopicSubscriberEvent event)
    {
        return new EventSubscriptionCreationResult(key);
    }

}
