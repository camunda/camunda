package org.camunda.tngp.client.task.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.collections.Long2ObjectHashMap;

public class TaskSubscriptions
{
    protected Long2ObjectHashMap<TaskSubscriptionImpl> subscriptions = new Long2ObjectHashMap<>();

    protected final List<TaskSubscriptionImpl> pollableSubscriptions = new CopyOnWriteArrayList<>();
    protected final List<TaskSubscriptionImpl> managedExecutionSubscriptions = new CopyOnWriteArrayList<>();

    public void addPollableSubscription(TaskSubscriptionImpl subscription)
    {
        this.subscriptions.put(subscription.getId(), subscription);
        this.pollableSubscriptions.add(subscription);
    }

    public void addManagedExecutionSubscription(TaskSubscriptionImpl subscription)
    {
        this.subscriptions.put(subscription.getId(), subscription);
        this.managedExecutionSubscriptions.add(subscription);
    }

    public void closeAll()
    {
        for (TaskSubscriptionImpl subscription : pollableSubscriptions)
        {
            subscription.close();
        }

        for (TaskSubscriptionImpl subscription : managedExecutionSubscriptions)
        {
            subscription.close();
        }
    }

    public List<TaskSubscriptionImpl> getManagedExecutionSubscriptions()
    {
        return managedExecutionSubscriptions;
    }

    public List<TaskSubscriptionImpl> getPollableSubscriptions()
    {
        return pollableSubscriptions;
    }

    public void removeSubscription(TaskSubscriptionImpl subscription)
    {
        subscriptions.remove(subscription.getId());
        pollableSubscriptions.remove(subscription);
        managedExecutionSubscriptions.remove(subscription);
    }

    public TaskSubscriptionImpl getSubscription(long id)
    {
        return subscriptions.get(id);
    }

}
