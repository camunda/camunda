package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.task.impl.TaskSubscriptionImpl;

public class TaskSubscriptionLifecycle implements SubscriptionLifecycle<TaskSubscriptionImpl>
{

    protected final TngpClientImpl client;

    public TaskSubscriptionLifecycle(TngpClientImpl client)
    {
        this.client = client;
    }

    @Override
    public Long requestNewSubscription(TaskSubscriptionImpl subscription)
    {
        return client.brokerTaskSubscription()
                .topicId(subscription.getTopicId())
                .taskType(subscription.getTaskType())
                .lockDuration(subscription.getLockTime())
                .lockOwner(subscription.getLockOwner())
                .initialCredits(subscription.capacity())
                .execute();
    }

    @Override
    public void requestSubscriptionClose(TaskSubscriptionImpl subscription)
    {
        client.closeBrokerTaskSubscription()
            .subscriptionId(subscription.getId())
            .topicId(subscription.getTopicId())
            .taskType(subscription.getTaskType())
            .execute();

    }

    @Override
    public void onEventsPolled(TaskSubscriptionImpl subscription, int numEvents)
    {
        if (subscription.isOpen())
        {
            final int credits = subscription.getAndDecrementRemainingCapacity();

            if (credits > 0)
            {
                client.updateSubscriptionCredits()
                    .subscriptionId(subscription.getId())
                    .topicId(subscription.getTopicId())
                    .taskType(subscription.getTaskType())
                    .credits(credits)
                    .execute();
            }
        }
    }

}
