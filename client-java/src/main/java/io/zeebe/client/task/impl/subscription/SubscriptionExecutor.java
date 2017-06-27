package io.zeebe.client.task.impl.subscription;

import io.zeebe.util.actor.Actor;

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
        return subscriptions.pollManagedSubscriptions();
    }

    @Override
    public String name()
    {
        return ROLE_NAME;
    }

}
