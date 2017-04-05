package org.camunda.tngp.client.task.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.camunda.tngp.client.event.impl.EventSubscription;

public class EventSubscriptions<T extends EventSubscription<T>>
{
    // topicId => subscriberKey => subscription (subscriber keys are not guaranteed to be globally unique)
    protected Int2ObjectHashMap<Long2ObjectHashMap<T>> subscriptions = new Int2ObjectHashMap<>();

    protected final List<T> pollableSubscriptions = new CopyOnWriteArrayList<>();
    protected final List<T> managedSubscriptions = new CopyOnWriteArrayList<>();

    protected void addPollableSubscription(T subscription)
    {
        this.subscriptions
            .computeIfAbsent(subscription.getTopicId(), (i) -> new Long2ObjectHashMap<>())
            .put(subscription.getSubscriberKey(), subscription);
        this.pollableSubscriptions.add(subscription);
    }

    protected void addManagedSubscription(T subscription)
    {
        this.subscriptions
            .computeIfAbsent(subscription.getTopicId(), (i) -> new Long2ObjectHashMap<>())
            .put(subscription.getSubscriberKey(), subscription);
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

    public void abortSubscriptionsOnChannel(int channelId)
    {
        for (T subscription : pollableSubscriptions)
        {
            if (subscription.getReceiveChannelId() == channelId)
            {
                subscription.abortAsync();
            }
        }

        for (T subscription : managedSubscriptions)
        {
            if (subscription.getReceiveChannelId() == channelId)
            {
                subscription.abortAsync();
            }
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
        final Long2ObjectHashMap<T> subscriptionsForTopic = subscriptions.get(subscription.getTopicId());
        if (subscriptionsForTopic != null)
        {
            subscriptionsForTopic.remove(subscription.getSubscriberKey());

            if (subscriptionsForTopic.isEmpty())
            {
                subscriptions.remove(subscription.getTopicId());
            }
        }

        pollableSubscriptions.remove(subscription);
        managedSubscriptions.remove(subscription);
    }

    public T getSubscription(int topicId, long subscriberKey)
    {
        final Long2ObjectHashMap<T> subscriptionsForTopic = subscriptions.get(topicId);
        if (subscriptionsForTopic != null)
        {
            return subscriptionsForTopic.get(subscriberKey);
        }
        else
        {
            return null;
        }
    }


}
