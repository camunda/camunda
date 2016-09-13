package org.camunda.tngp.client.task.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TaskSubscriptions
{
    protected List<TaskSubscriptionImpl> pollableSubscriptions = new CopyOnWriteArrayList<>();
    protected List<TaskSubscriptionImpl> managedExecutionSubscriptions = new CopyOnWriteArrayList<>();

    public void addPollableSubscription(TaskSubscriptionImpl subscription)
    {
        this.pollableSubscriptions.add(subscription);
    }

    public void addManagedExecutionSubscription(TaskSubscriptionImpl subscription)
    {
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
        pollableSubscriptions.remove(subscription);
        managedExecutionSubscriptions.remove(subscription);
    }

}
