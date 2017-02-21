package org.camunda.tngp.client.task.impl;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.client.event.impl.EventSubscription;

public class SubscriptionExecutor implements Agent
{
    public static final String ROLE_NAME = "subscription-executor";

    protected final EventSubscriptions<?> subscriptions;

    public SubscriptionExecutor(EventSubscriptions<?> subscriptions)
    {
        this.subscriptions = subscriptions;
    }

    @Override
    public int doWork() throws Exception
    {
        return pollManagedSubscriptions(subscriptions);
    }

    protected int pollManagedSubscriptions(EventSubscriptions<?> subscriptions)
    {
        int workCount = 0;
        for (EventSubscription<?> subscription : subscriptions.getManagedSubscriptions())
        {
            workCount += subscription.poll();
        }
        return workCount;
    }

    @Override
    public String roleName()
    {
        return ROLE_NAME;
    }

}
