package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;
import org.camunda.tngp.util.CheckedConsumer;
import org.camunda.tngp.util.EnsureUtil;

public class TopicSubscriptionBuilderImpl implements TopicSubscriptionBuilder
{

    protected TopicSubscriptionImplBuilder implBuilder;

    public TopicSubscriptionBuilderImpl(int topicId, EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        implBuilder = new TopicSubscriptionImplBuilder(topicId, acquisition);
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
        EnsureUtil.ensureGreaterThanOrEqual("topicId", implBuilder.getTopicId(), 0);

        final TopicSubscriptionImpl subscription = implBuilder.build();
        subscription.open();
        return subscription;
    }

}
