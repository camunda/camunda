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

import org.agrona.collections.Long2LongHashMap;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.EnsureUtil;

public class TopicSubscriberGroupBuilder
{
    protected final ZeebeClient client;
    protected final ClientTopologyManager topologyManager;

    protected final String topic;
    protected CheckedConsumer<GeneralEventImpl> handler;
    protected final EventAcquisition acquisition;
    protected String name;
    protected final int prefetchCapacity;
    protected boolean forceStart;
    protected long defaultStartPosition;
    protected final Long2LongHashMap startPositions = new Long2LongHashMap(-1);

    public TopicSubscriberGroupBuilder(
            ZeebeClient client,
            ClientTopologyManager topologyManager,
            String topic,
            EventAcquisition acquisition,
            int prefetchCapacity)
    {
        EnsureUtil.ensureNotNull("topic", topic);
        EnsureUtil.ensureNotEmpty("topic", topic);

        this.client = client;
        this.topologyManager = topologyManager;
        this.topic = topic;
        this.acquisition = acquisition;
        this.prefetchCapacity = prefetchCapacity;
        startAtTailOfTopic();
    }

    public TopicSubscriberGroupBuilder handler(CheckedConsumer<GeneralEventImpl> handler)
    {
        this.handler = handler;
        return this;
    }

    public TopicSubscriberGroupBuilder startPosition(int partitionId, long startPosition)
    {
        this.startPositions.put(partitionId, startPosition);
        return this;
    }

    protected TopicSubscriberGroupBuilder defaultStartPosition(long position)
    {
        this.defaultStartPosition = position;
        return this;
    }

    public TopicSubscriberGroupBuilder startAtTailOfTopic()
    {
        return defaultStartPosition(-1L);
    }

    public TopicSubscriberGroupBuilder startAtHeadOfTopic()
    {
        return defaultStartPosition(0L);
    }

    public TopicSubscriberGroupBuilder forceStart()
    {
        this.forceStart = true;
        return this;
    }

    public TopicSubscriberGroupBuilder name(String name)
    {
        this.name = name;
        return this;
    }

    public CheckedConsumer<GeneralEventImpl> getHandler()
    {
        return handler;
    }

    public String getName()
    {
        return name;
    }

    public TopicSubscriberGroup build()
    {
        final TopicSubscriptionSpec subscription = new TopicSubscriptionSpec(
                topic,
                handler,
                defaultStartPosition,
                startPositions,
                forceStart,
                name,
                prefetchCapacity);

        final TopicSubscriberGroup subscriberGroup = new TopicSubscriberGroup(
                client,
                acquisition,
                subscription);

        return subscriberGroup;
    }
}
