package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.PollableTopicSubscription;
import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.util.EnsureUtil;

public class PollableTopicSubscriptionBuilderImpl implements PollableTopicSubscriptionBuilder
{
    protected TopicSubscriptionImplBuilder implBuilder;

    public PollableTopicSubscriptionBuilderImpl(int topicId, EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        implBuilder = new TopicSubscriptionImplBuilder(topicId, acquisition);
    }

    @Override
    public PollableTopicSubscription open()
    {
        EnsureUtil.ensureGreaterThanOrEqual("topicId", implBuilder.getTopicId(), 0);

        final TopicSubscriptionImpl subscription = implBuilder.build();
        subscription.open();
        return subscription;
    }

}
