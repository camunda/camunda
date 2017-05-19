package org.camunda.tngp.client.task.impl.subscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.camunda.tngp.client.event.impl.EventSubscription;

public class EventSubscriptions<T extends EventSubscription<T>>
{
    // topicName => partitionId => subscriberKey => subscription (subscriber keys are not guaranteed to be globally unique)
    protected Map<String, Int2ObjectHashMap<Long2ObjectHashMap<T>>> subscriptions = new HashMap<>();

    protected final List<T> pollableSubscriptions = new CopyOnWriteArrayList<>();
    protected final List<T> managedSubscriptions = new CopyOnWriteArrayList<>();

    public void addSubscription(final T subscription)
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

    protected void addPollableSubscription(final T subscription)
    {
        addSubscriptionForTopic(subscription);
        this.pollableSubscriptions.add(subscription);
    }

    protected void addManagedSubscription(final T subscription)
    {
        addSubscriptionForTopic(subscription);
        this.managedSubscriptions.add(subscription);
    }

    protected void addSubscriptionForTopic(final T subscription)
    {
        this.subscriptions
            .computeIfAbsent(subscription.getTopicName(), topicName -> new Int2ObjectHashMap<>())
            .computeIfAbsent(subscription.getPartitionId(), partitionId -> new Long2ObjectHashMap<>())
            .put(subscription.getSubscriberKey(), subscription);
    }

    public void closeAll()
    {
        for (final T subscription : pollableSubscriptions)
        {
            subscription.close();
        }

        for (final T subscription : managedSubscriptions)
        {
            subscription.close();
        }
    }

    public void abortSubscriptionsOnChannel(final int channelId)
    {
        for (final T subscription : pollableSubscriptions)
        {
            if (subscription.getReceiveChannelId() == channelId)
            {
                subscription.abortAsync();
            }
        }

        for (final T subscription : managedSubscriptions)
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

    public void removeSubscription(final T subscription)
    {
        final String topicName = subscription.getTopicName();
        final int partitionId = subscription.getPartitionId();

        final Int2ObjectHashMap<Long2ObjectHashMap<T>> subscriptionsForTopic = subscriptions.get(topicName);
        if (subscriptionsForTopic != null)
        {
            final Long2ObjectHashMap<T> subscriptionsForPartition = subscriptionsForTopic.get(partitionId);
            if (subscriptionsForPartition != null)
            {
                subscriptionsForPartition.remove(subscription.getSubscriberKey());

                if (subscriptionsForPartition.isEmpty())
                {
                    subscriptionsForTopic.remove(partitionId);
                }

                if (subscriptionsForTopic.isEmpty())
                {
                    subscriptions.remove(topicName);
                }
            }
        }

        pollableSubscriptions.remove(subscription);
        managedSubscriptions.remove(subscription);
    }

    public T getSubscription(final String topicName, final int partitionId, final long subscriberKey)
    {
        final Int2ObjectHashMap<Long2ObjectHashMap<T>> subscriptionsForTopic = subscriptions.get(topicName);

        if (subscriptionsForTopic != null)
        {
            final Long2ObjectHashMap<T> subscriptionsForPartition = subscriptionsForTopic.get(partitionId);

            if (subscriptionsForPartition != null)
            {
                return subscriptionsForPartition.get(subscriberKey);
            }

        }

        return null;
    }


}
