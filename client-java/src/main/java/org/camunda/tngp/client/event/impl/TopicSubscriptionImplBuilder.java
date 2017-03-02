package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.util.CheckedConsumer;

public class TopicSubscriptionImplBuilder
{
    protected static final int PREFETCH_SIZE = 32;

    protected final TopicClientImpl client;
    protected CheckedConsumer<TopicEventImpl> handler;
    protected long startPosition;
    protected final EventAcquisition<TopicSubscriptionImpl> acquisition;

    public TopicSubscriptionImplBuilder(TopicClientImpl client, EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        this.client = client;
        this.acquisition = acquisition;
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

    public CheckedConsumer<TopicEventImpl> getHandler()
    {
        return handler;
    }

    public TopicSubscriptionImpl build()
    {
        return new TopicSubscriptionImpl(
            client,
            handler,
            acquisition,
            PREFETCH_SIZE,
            startPosition);
    }
}
