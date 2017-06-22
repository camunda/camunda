package org.camunda.tngp.client.event.impl;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.client.impl.Loggers;
import org.camunda.tngp.client.task.impl.subscription.EventSubscriptionCreationResult;
import org.camunda.tngp.client.task.impl.subscription.EventSubscriptions;
import org.camunda.tngp.client.task.impl.subscription.SubscribedEventHandler;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.actor.Actor;
import org.slf4j.Logger;

public class EventAcquisition<T extends EventSubscription<T>> implements SubscribedEventHandler, Actor
{

    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    protected final EventSubscriptions<T> subscriptions;
    protected DeferredCommandContext asyncContext;
    protected String name;


    public EventAcquisition(String name, EventSubscriptions<T> subscriptions)
    {
        this.name = name;
        this.asyncContext = new DeferredCommandContext();
        this.subscriptions = subscriptions;
    }

    public EventAcquisition(EventSubscriptions<T> subscriptions)
    {
        this("event-acquisition", subscriptions);
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = asyncContext.doWork();
        workCount += manageSubscriptions();

        return workCount;
    }

    public CompletableFuture<Void> openSubscriptionAsync(T subscription)
    {
        return asyncContext.runAsync((future) ->
        {
            final EventSubscriptionCreationResult result = subscription.requestNewSubscription();

            subscription.setSubscriberKey(result.getSubscriberKey());
            subscription.setReceiveChannelId(result.getReceiveChannelId());
            subscriptions.addSubscription(subscription);
            future.complete(null);
        });
    }

    protected void closeSubscription(T subscription)
    {
        try
        {
            subscription.requestSubscriptionClose();
            subscriptions.removeSubscription(subscription);
            subscription.onClose();
        }
        catch (Exception e)
        {
            LOGGER.warn("Exception when closing subscription", e);
            subscription.onCloseFailed(e);
        }
    }

    protected void abort(T subscription)
    {
        // don't try to send any further requests regarding this subscription
        subscriptions.removeSubscription(subscription);
        subscription.onAbort();
    }

    @Override
    public boolean onEvent(long subscriberKey, TopicEventImpl event)
    {
        final T subscription = subscriptions.getSubscription(event.getTopicName(), event.getPartitionId(), subscriberKey);

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

    public int manageSubscriptions()
    {
        int workCount = 0;

        workCount += manageSubscriptions(subscriptions.getManagedSubscriptions());
        workCount += manageSubscriptions(subscriptions.getPollableSubscriptions());

        return workCount;
    }

    protected int manageSubscriptions(Collection<T> subscriptions)
    {
        int workCount = 0;

        for (T subscription : subscriptions)
        {
            if (subscription.isClosing() && !subscription.hasEventsInProcessing())
            {
                closeSubscription(subscription);
                workCount++;
            }
            if (subscription.isOpen())
            {
                try
                {
                    subscription.replenishEventSource();
                }
                catch (Exception e)
                {
                    LOGGER.warn("Could not replenish subscription event source", e);
                }
            }
        }

        return workCount;
    }

}
