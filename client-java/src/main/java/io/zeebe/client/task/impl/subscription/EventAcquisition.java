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

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.impl.GeneralEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;

public class EventAcquisition implements SubscribedEventHandler, Actor
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    protected final String name;
    protected final EventSubscribers subscribers;
    protected DeferredCommandContext asyncContext = new DeferredCommandContext();

    public EventAcquisition(String name, EventSubscribers subscriptions)
    {
        this.name = name;
        this.subscribers = subscriptions;
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = asyncContext.doWork();
        workCount += subscribers.maintainState();
        return workCount;
    }

    @Override
    public boolean onEvent(long subscriberKey, GeneralEventImpl event)
    {
        final EventMetadata eventMetadata = event.getMetadata();
        EventSubscriber subscriber = subscribers.getSubscriber(eventMetadata.getPartitionId(), subscriberKey);

        if (subscriber == null)
        {
            if (subscribers.isAnySubscriberOpening())
            {
                // avoids a race condition when a subscribe request is in progress and we haven't activated the subscriber
                // yet, but we already receive an event from the broker
                // in this case, we postpone the event
                return false;
            }
            else
            {
                // fetch a second time as the subscriber may have opened (and registered) between the first #getSubscriptions
                // invocation and the check for opening subscribers
                subscriber = subscribers.getSubscriber(eventMetadata.getPartitionId(), subscriberKey);
            }
        }

        if (subscriber != null && subscriber.isOpen())
        {
            event.setTopicName(subscriber.getTopicName());
            return subscriber.addEvent(event);
        }
        else
        {
            LOGGER.debug(name() + ": Ignoring event " + event.toString() + " for subscription " + subscriberKey);
            return true; // ignoring the event is success; don't want to retry it later
        }
    }

    public void activateSubscriber(EventSubscriber subscriber)
    {
        this.subscribers.activate(subscriber);
    }

    public void addSubscriber(EventSubscriber subscriber)
    {
        this.subscribers.addSubscriber(subscriber);
    }

    public void deactivateSubscriber(EventSubscriber subscriber)
    {
        this.subscribers.deactivate(subscriber);
    }

    public void removeSubscriber(EventSubscriber subscriber)
    {
        this.subscribers.removeSubscriber(subscriber);
    }

    @SuppressWarnings("rawtypes")
    public void stopManageGroup(EventSubscriberGroup subscription)
    {
        this.subscribers.removeGroup(subscription);
    }

    @SuppressWarnings("rawtypes")
    public CompletableFuture<Void> registerSubscriptionAsync(EventSubscriberGroup subscription)
    {
        return asyncContext.runAsync((future) ->
        {
            subscribers.addGroup(subscription);
        });
    }

    public void reopenSubscriptionsForRemoteAsync(RemoteAddress remoteAddress)
    {
        asyncContext.runAsync(() -> subscribers.reopenSubscribersForRemote(remoteAddress));
    }

}
