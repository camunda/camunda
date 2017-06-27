package io.zeebe.client.event.impl;

import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.util.CheckedConsumer;

public class TopicSubscriptionImplBuilder
{
    protected final TopicClientImpl client;
    protected CheckedConsumer<TopicEventImpl> handler;
    protected long startPosition;
    protected final EventAcquisition<TopicSubscriptionImpl> acquisition;
    protected String name;
    protected final int prefetchCapacity;
    protected boolean forceStart;

    public TopicSubscriptionImplBuilder(
            TopicClientImpl client,
            EventAcquisition<TopicSubscriptionImpl> acquisition,
            int prefetchCapacity)
    {
        this.client = client;
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
                handler,
                prefetchCapacity,
                startPosition,
                forceStart,
                name,
                acquisition);

        this.acquisition.newSubscriptionAsync(subscription);

        return subscription;
    }
}
