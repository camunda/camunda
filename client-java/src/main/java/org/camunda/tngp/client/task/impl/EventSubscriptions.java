package org.camunda.tngp.client.task.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.collections.Long2ObjectHashMap;
import org.camunda.tngp.client.event.impl.EventSubscription;

public class EventSubscriptions<T extends EventSubscription<T>>
{
    protected Long2ObjectHashMap<T> subscriptions = new Long2ObjectHashMap<>();

    protected final List<T> pollableSubscriptions = new CopyOnWriteArrayList<>();
    protected final List<T> managedSubscriptions = new CopyOnWriteArrayList<>();

    protected void addPollableSubscription(T subscription)
    {
        this.subscriptions.put(subscription.getId(), subscription);
        this.pollableSubscriptions.add(subscription);
    }

    protected void addManagedSubscription(T subscription)
    {
        this.subscriptions.put(subscription.getId(), subscription);
        this.managedSubscriptions.add(subscription);
    }

    public void addSubscription(T subscription)
    {
        if (subscription.isManagedSubscription())
        {
            addManagedSubscription(subscription);
        }
        else
        {
            addPollableSubscription(subscription);
        }
    }

    public void closeAll()
    {
        for (T subscription : pollableSubscriptions)
        {
            subscription.close();
        }

        for (T subscription : managedSubscriptions)
        {
            subscription.close();
        }
    }

    public List<T> getManagedSubscriptions()
    {
        return managedSubscriptions;
    }

    public List<T> getPollableSubscriptions()
    {
        return pollableSubscriptions;
    }

    public void removeSubscription(T subscription)
    {
        subscriptions.remove(subscription.getId());
        pollableSubscriptions.remove(subscription);
        managedSubscriptions.remove(subscription);
    }

    public T getSubscription(long id)
    {
        return subscriptions.get(id);
    }


}
