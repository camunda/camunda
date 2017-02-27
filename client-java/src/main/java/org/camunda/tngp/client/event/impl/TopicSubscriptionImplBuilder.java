package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.util.CheckedConsumer;

public class TopicSubscriptionImplBuilder
{
    protected static final int PREFETCH_SIZE = 32;

    protected final TopicClientImpl client;
    protected CheckedConsumer<TopicEventImpl> handler;
    protected final EventAcquisition<TopicSubscriptionImpl> acquisition;

    public TopicSubscriptionImplBuilder(TopicClientImpl client, EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        this.client = client;
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

    public TopicSubscriptionImpl build()
    {
        return new TopicSubscriptionImpl(
            client,
            handler,
            acquisition,
            PREFETCH_SIZE);
    }
}
