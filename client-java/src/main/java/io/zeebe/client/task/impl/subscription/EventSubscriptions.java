/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.task.impl.subscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;

import io.zeebe.client.impl.Loggers;
import io.zeebe.transport.RemoteAddress;

public class EventSubscriptions<T extends EventSubscription<T>>
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    // topicName => partitionId => subscriberKey => subscription (subscriber keys are not guaranteed to be globally unique)
    protected Map<String, Int2ObjectHashMap<Long2ObjectHashMap<T>>> activeSubscriptions = new HashMap<>();

    protected final List<T> pollableSubscriptions = new CopyOnWriteArrayList<>();
    protected final List<T> managedSubscriptions = new CopyOnWriteArrayList<>();

    public void add(final T subscription)
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
        this.pollableSubscriptions.add(subscription);
    }

    protected void addManagedSubscription(final T subscription)
    {
        this.managedSubscriptions.add(subscription);
    }

    public void closeAll()
    {
        for (final T subscription : pollableSubscriptions)
        {
            closeSubscription(subscription);
        }

        for (final T subscription : managedSubscriptions)
        {
            closeSubscription(subscription);
        }
    }

    protected void closeSubscription(EventSubscription<T> subscription)
    {
        try
        {
            subscription.close();
        }
        catch (final Exception e)
        {
            LOGGER.error("Unable to close subscription with key: " + subscription.getSubscriberKey(), e);
        }
    }

    public void reopenSubscriptionsForRemote(RemoteAddress remoteAddress)
    {
        forAllDoConsume(managedSubscriptions, s ->
        {
            // s.getEventSource is null if the subscription is not yet opened
            if (remoteAddress.equals(s.getEventSource()))
            {
                s.reopenAsync();
            }
        });

        forAllDoConsume(pollableSubscriptions, s ->
        {
            if (remoteAddress.equals(s.getEventSource()))
            {
                s.reopenAsync();
            }
        });
    }

    protected void doForSubscriptionsWithRemote(List<T> subscriptions, RemoteAddress remoteAddress, Consumer<T> action)
    {
        for (int i = 0; i < subscriptions.size(); i++)
        {
            final T subscription = subscriptions.get(i);
            if (subscription.getEventSource().equals(remoteAddress))
            {
                action.accept(subscription);
            }
        }
    }

    public void remove(final T subscription)
    {
        final String topicName = subscription.getTopicName();
        final int partitionId = subscription.getPartitionId();

        final Int2ObjectHashMap<Long2ObjectHashMap<T>> subscriptionsForTopic = activeSubscriptions.get(topicName);
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
                    activeSubscriptions.remove(topicName);
                }
            }
        }

        pollableSubscriptions.remove(subscription);
        managedSubscriptions.remove(subscription);
    }

    public T getSubscription(final String topicName, final int partitionId, final long subscriberKey)
    {
        final Int2ObjectHashMap<Long2ObjectHashMap<T>> subscriptionsForTopic = activeSubscriptions.get(topicName);

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

    public void activate(T subscription)
    {
        this.activeSubscriptions
            .computeIfAbsent(subscription.getTopicName(), topicName -> new Int2ObjectHashMap<>())
            .computeIfAbsent(subscription.getPartitionId(), partitionId -> new Long2ObjectHashMap<>())
            .put(subscription.getSubscriberKey(), subscription);
    }

    public int maintainState()
    {
        int workCount = forAllDo(managedSubscriptions, s -> s.maintainState());
        workCount += forAllDo(pollableSubscriptions, s -> s.maintainState());
        return workCount;
    }

    protected int forAllDo(List<T> subscriptions, ToIntFunction<T> action)
    {
        int workCount = 0;

        for (T subscription : subscriptions)
        {
            workCount += action.applyAsInt(subscription);
        }

        return workCount;
    }

    protected void forAllDoConsume(List<T> subscriptions, Consumer<T> action)
    {
        for (T subscription : subscriptions)
        {
            action.accept(subscription);
        }
    }

    public int pollManagedSubscriptions()
    {
        return forAllDo(managedSubscriptions, s -> s.poll());
    }

}
