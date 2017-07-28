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

import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.EnsureUtil;

public class TopicSubscriptionImplBuilder
{
    protected final TopicClientImpl client;
    protected final String topic;
    protected final int partitionId;
    protected CheckedConsumer<TopicEventImpl> handler;
    protected long startPosition;
    protected final EventAcquisition<TopicSubscriptionImpl> acquisition;
    protected String name;
    protected final int prefetchCapacity;
    protected boolean forceStart;

    public TopicSubscriptionImplBuilder(
            TopicClientImpl client,
            String topic,
            int partitionId,
            EventAcquisition<TopicSubscriptionImpl> acquisition,
            int prefetchCapacity)
    {
        EnsureUtil.ensureNotNull("topic", topic);
        EnsureUtil.ensureNotEmpty("topic", topic);

        this.client = client;
        this.topic = topic;
        this.partitionId = partitionId;
        this.acquisition = acquisition;
        this.prefetchCapacity = prefetchCapacity;
        startAtTailOfTopic();
    }

    public TopicSubscriptionImplBuilder handler(CheckedConsumer<TopicEventImpl> handler)
    {
        this.handler = handler;
        return this;
    }

    public TopicSubscriptionImplBuilder startPosition(long startPosition)
    {
        this.startPosition = startPosition;
        return this;
    }

    public TopicSubscriptionImplBuilder startAtTailOfTopic()
    {
        return startPosition(-1L);
    }

    public TopicSubscriptionImplBuilder startAtHeadOfTopic()
    {
        return startPosition(0L);
    }

    public TopicSubscriptionImplBuilder forceStart()
    {
        this.forceStart = true;
        return this;
    }

    public TopicSubscriptionImplBuilder name(String name)
    {
        this.name = name;
        return this;
    }

    public CheckedConsumer<TopicEventImpl> getHandler()
    {
        return handler;
    }

    public String getName()
    {
        return name;
    }

    public TopicSubscriptionImpl build()
    {
        final TopicSubscriptionImpl subscription = new TopicSubscriptionImpl(
                client,
                topic,
                partitionId,
                handler,
                prefetchCapacity,
                startPosition,
                forceStart,
                name,
                acquisition);

        this.acquisition.registerSubscriptionAsync(subscription);

        return subscription;
    }
}
