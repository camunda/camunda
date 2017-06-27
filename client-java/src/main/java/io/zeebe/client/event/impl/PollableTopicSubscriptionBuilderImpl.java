package io.zeebe.client.event.impl;

import io.zeebe.client.event.PollableTopicSubscription;
import io.zeebe.client.event.PollableTopicSubscriptionBuilder;
import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.util.EnsureUtil;

public class PollableTopicSubscriptionBuilderImpl implements PollableTopicSubscriptionBuilder
{
    protected TopicSubscriptionImplBuilder implBuilder;

    public PollableTopicSubscriptionBuilderImpl(
            TopicClientImpl client,
            EventAcquisition<TopicSubscriptionImpl> acquisition,
            int prefetchCapacity)
    {
        implBuilder = new TopicSubscriptionImplBuilder(client, acquisition, prefetchCapacity);
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

    @Override
    public PollableTopicSubscriptionBuilder forcedStart()
    {
        implBuilder.forceStart();
        return this;
    }

}
