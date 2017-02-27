package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.PollableTopicSubscription;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;

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
        final TopicSubscriptionImpl subscription = implBuilder.build();
        subscription.open();
        return subscription;
    }

}
