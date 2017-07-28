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
import io.zeebe.client.event.impl.TopicEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;

public class EventAcquisition<T extends EventSubscription<T>> implements SubscribedEventHandler, Actor
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    protected final String name;
    protected final EventSubscriptions<T> subscriptions;
    protected DeferredCommandContext asyncContext = new DeferredCommandContext();

    public EventAcquisition(String name, EventSubscriptions<T> subscriptions)
    {
        this.name = name;
        this.subscriptions = subscriptions;
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
        workCount += subscriptions.maintainState();
        return workCount;
    }

    @Override
    public boolean onEvent(long subscriberKey, TopicEventImpl event)
    {
        final EventMetadata eventMetadata = event.getMetadata();
        final T subscription = subscriptions.getSubscription(eventMetadata.getTopicName(), eventMetadata.getPartitionId(), subscriberKey);

        if (subscription != null && subscription.isOpen())
        {
            return subscription.addEvent(event);
        }
        else
        {
            LOGGER.debug(name() + ": Ignoring event " + event.toString() + " for subscription " + subscriberKey);
            return true; // ignoring the event is success; don't want to retry it later
        }
    }

    public void activateSubscription(T subscription)
    {
        this.subscriptions.activate(subscription);
    }

    public void stopManageSubscription(T subscription)
    {
        this.subscriptions.remove(subscription);
    }

    public CompletableFuture<T> registerSubscriptionAsync(T subscription)
    {
        return asyncContext.runAsync((future) ->
        {
            subscriptions.add(subscription);
        });
    }

}
