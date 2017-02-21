package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.util.CheckedConsumer;

public class TopicSubscriptionImplBuilder
{
    protected static final int PREFETCH_SIZE = 32;

    protected int topicId;
    protected CheckedConsumer<TopicEventImpl> handler;
    protected final EventAcquisition<TopicSubscriptionImpl> acquisition;

    public TopicSubscriptionImplBuilder(int topicId, EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        this.topicId = topicId;
        this.acquisition = acquisition;
    }

    public TopicSubscriptionImplBuilder handler(CheckedConsumer<TopicEventImpl> handler)
    {
        this.handler = handler;
        return this;
    }

    public CheckedConsumer<TopicEventImpl> getHandler()
    {
        return handler;
    }

    public int getTopicId()
    {
        return topicId;
    }

    public TopicSubscriptionImpl build()
    {
        return new TopicSubscriptionImpl(
            handler,
            topicId,
            acquisition,
            PREFETCH_SIZE);
    }
}
