package org.camunda.tngp.client.task.impl.subscription;

import org.camunda.tngp.client.event.impl.EventSubscription;
import org.camunda.tngp.util.actor.Actor;

public class SubscriptionExecutor implements Actor
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
    public String name()
    {
        return ROLE_NAME;
    }

}
