package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.impl.TngpClientImpl;

public class TopicSubscriptionLifecycle implements SubscriptionLifecycle<TopicSubscriptionImpl>
{

    protected final TngpClientImpl client;

    public TopicSubscriptionLifecycle(TngpClientImpl client)
    {
        this.client = client;
    }

    @Override
    public Long requestNewSubscription(TopicSubscriptionImpl subscription)
    {
        return client.createTopicSubscription()
            .topicId(subscription.getTopicId())
            .execute();
    }

    @Override
    public void requestSubscriptionClose(TopicSubscriptionImpl subscription)
    {
        client.closeTopicSubscription()
            .id(subscription.getId())
            .execute();

    }

    @Override
    public void onEventsPolled(TopicSubscriptionImpl subscription, int numEvents)
    {
    }

}
