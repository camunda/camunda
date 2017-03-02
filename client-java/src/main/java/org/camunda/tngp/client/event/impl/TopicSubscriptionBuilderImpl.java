package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;
import org.camunda.tngp.util.CheckedConsumer;
import org.camunda.tngp.util.EnsureUtil;

public class TopicSubscriptionBuilderImpl implements TopicSubscriptionBuilder
{

    protected TopicSubscriptionImplBuilder implBuilder;

    public TopicSubscriptionBuilderImpl(TopicClientImpl client, EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        implBuilder = new TopicSubscriptionImplBuilder(client, acquisition);
    }

    @Override
    public TopicSubscriptionBuilder handler(TopicEventHandler handler)
    {
        return handler((e) -> handler.handle(e, e));
    }

    public TopicSubscriptionBuilder handler(CheckedConsumer<TopicEventImpl> handler)
    {
        EnsureUtil.ensureNotNull("handler", handler);
        implBuilder.handler(handler);
        return this;
    }

    @Override
    public TopicSubscription open()
    {
        EnsureUtil.ensureNotNull("handler", implBuilder.getHandler());
        EnsureUtil.ensureNotNull("name", implBuilder.getName());

        final TopicSubscriptionImpl subscription = implBuilder.build();
        subscription.open();
        return subscription;
    }

    @Override
    public TopicSubscriptionBuilder startAtPosition(long position)
    {
        implBuilder.startPosition(position);
        return this;
    }

    @Override
    public TopicSubscriptionBuilder startAtTailOfTopic()
    {
        implBuilder.startAtTailOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilder startAtHeadOfTopic()
    {
        implBuilder.startAtHeadOfTopic();
        return this;
    }

    @Override
    public TopicSubscriptionBuilder name(String name)
    {
        implBuilder.name(name);
        return this;
    }

}
