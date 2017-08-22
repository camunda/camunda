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

import io.zeebe.client.TopicsClient;
import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.Event;
import io.zeebe.client.event.PollableTopicSubscriptionBuilder;
import io.zeebe.client.event.TopicSubscriptionBuilder;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.topic.impl.CreateTopicCommandImpl;


public class TopicClientImpl implements TopicsClient
{
    protected final ZeebeClientImpl client;

    public TopicClientImpl(final ZeebeClientImpl client)
    {
        this.client = client;
    }

    @Override
    public TopicSubscriptionBuilder newSubscription(String topicName)
    {
        return client.getSubscriptionManager().newTopicSubscription(this, topicName);
    }

    @Override
    public PollableTopicSubscriptionBuilder newPollableSubscription(String topicName)
    {
        return client.getSubscriptionManager().newPollableTopicSubscription(this, topicName);
    }

    @Override
    public Request<Event> create(String topicName, int partitions)
    {
        return new CreateTopicCommandImpl(client.getCommandManager(), topicName, partitions);
    }

    public CreateTopicSubscriptionCommandImpl createTopicSubscription(String topicName, int partitionId)
    {
        return new CreateTopicSubscriptionCommandImpl(client.getCommandManager(), topicName, partitionId);
    }

    public CloseTopicSubscriptionCommandImpl closeTopicSubscription(String topicName, int partitionId, long subscriberKey)
    {
        return new CloseTopicSubscriptionCommandImpl(client.getCommandManager(), topicName, partitionId, subscriberKey);
    }

    public AcknowledgeSubscribedEventCommandImpl acknowledgeEvent(String topicName, int partitionId)
    {
        return new AcknowledgeSubscribedEventCommandImpl(client.getCommandManager(), topicName, partitionId);
    }

}
