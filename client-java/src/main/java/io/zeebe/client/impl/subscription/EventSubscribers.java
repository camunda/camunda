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
package io.zeebe.client.impl.subscription;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;

import io.zeebe.client.impl.Loggers;
import io.zeebe.transport.RemoteAddress;

@SuppressWarnings("rawtypes")
public class EventSubscribers
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    // partitionId => subscriberKey => subscription (subscriber keys are not guaranteed to be globally unique)
    protected Int2ObjectHashMap<Long2ObjectHashMap<Subscriber>> subscribers = new Int2ObjectHashMap<>();

    protected final List<SubscriberGroup> pollableSubscriberGroups = new CopyOnWriteArrayList<>();
    protected final List<SubscriberGroup> managedSubscriberGroups = new CopyOnWriteArrayList<>();

    public void addGroup(final SubscriberGroup subscription)
    {
        if (subscription.isManagedGroup())
        {
            addManagedGroup(subscription);
        }
        else
        {
            addPollableGroup(subscription);
        }
    }

    private void addPollableGroup(final SubscriberGroup subscription)
    {
        this.pollableSubscriberGroups.add(subscription);
    }

    private void addManagedGroup(final SubscriberGroup subscription)
    {
        this.managedSubscriberGroups.add(subscription);
    }

    public void closeAllGroups(String reason)
    {
        forAllDoConsume(pollableSubscriberGroups, group -> group.initClose(reason, null));
        forAllDoConsume(managedSubscriberGroups, group -> group.initClose(reason, null));
    }

    public void add(Subscriber subscriber)
    {
        this.subscribers
            .computeIfAbsent(subscriber.getPartitionId(), partitionId -> new Long2ObjectHashMap<>())
            .put(subscriber.getSubscriberKey(), subscriber);
    }

    public void remove(final Subscriber subscriber)
    {
        final int partitionId = subscriber.getPartitionId();

        final Long2ObjectHashMap<Subscriber> subscribersForPartition = subscribers.get(partitionId);
        if (subscribersForPartition != null)
        {
            subscribersForPartition.remove(subscriber.getSubscriberKey());

            if (subscribersForPartition.isEmpty())
            {
                subscribers.remove(partitionId);
            }
        }

    }

    public void removeGroup(SubscriberGroup group)
    {
        pollableSubscriberGroups.remove(group);
        managedSubscriberGroups.remove(group);
    }

    public Subscriber getSubscriber(final int partitionId, final long subscriberKey)
    {
        final Long2ObjectHashMap<Subscriber> subscribersForPartition = subscribers.get(partitionId);

        if (subscribersForPartition != null)
        {
            return subscribersForPartition.get(subscriberKey);
        }

        return null;
    }

    private int forAllDo(List<SubscriberGroup> groups, ToIntFunction<SubscriberGroup> action)
    {
        int workCount = 0;

        for (SubscriberGroup group : groups)
        {
            workCount += action.applyAsInt(group);
        }

        return workCount;
    }

    private void forAllDoConsume(List<SubscriberGroup> groups, Consumer<SubscriberGroup> action)
    {
        for (SubscriberGroup subscription : groups)
        {
            action.accept(subscription);
        }
    }

    public void reopenSubscribersForRemote(RemoteAddress remote)
    {
        forAllDoConsume(managedSubscriberGroups, s -> s.reopenSubscriptionsForRemoteAsync(remote));
        forAllDoConsume(pollableSubscriberGroups, s -> s.reopenSubscriptionsForRemoteAsync(remote));
    }

    public int pollManagedSubscribers()
    {
        return forAllDo(managedSubscriberGroups, s -> s.poll());
    }

    public boolean isAnySubscriberOpeningOn(int partitionId)
    {
        return isAnySubscriberOpeningOn(managedSubscriberGroups, partitionId)
                || isAnySubscriberOpeningOn(pollableSubscriberGroups, partitionId);
    }

    private boolean isAnySubscriberOpeningOn(List<SubscriberGroup> groups, int partitionId)
    {
        for (SubscriberGroup group : groups)
        {
            if (group.isSubscribingTo(partitionId))
            {
                return true;
            }
        }

        return false;
    }
}
