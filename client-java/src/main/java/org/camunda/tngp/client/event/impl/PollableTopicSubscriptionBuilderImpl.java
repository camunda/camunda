package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.PollableTopicSubscription;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.util.EnsureUtil;

public class PollableTopicSubscriptionBuilderImpl implements PollableTopicSubscriptionBuilder
{
    protected TopicSubscriptionImplBuilder implBuilder;

    public PollableTopicSubscriptionBuilderImpl(TopicClientImpl client, EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        implBuilder = new TopicSubscriptionImplBuilder(client, acquisition);
    }

    @Override
    public PollableTopicSubscription open()
    {
        EnsureUtil.ensureNotNull("name", implBuilder.getName());

        final TopicSubscriptionImpl subscription = implBuilder.build();
        subscription.open();
        return subscription;
    }

    @Override
    public PollableTopicSubscriptionBuilder startAtPosition(long position)
    {
        implBuilder.startPosition(position);
        return this;
    }

    @Override
    public PollableTopicSubscriptionBuilder startAtTailOfTopic()
    {
        implBuilder.startAtTailOfTopic();
        return this;
    }

    @Override
    public PollableTopicSubscriptionBuilder startAtHeadOfTopic()
    {
        implBuilder.startAtHeadOfTopic();
        return this;
    }

    @Override
    public PollableTopicSubscriptionBuilder name(String subscriptionName)
    {
        implBuilder.name(subscriptionName);
        return this;
    }

}
