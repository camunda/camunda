package org.camunda.tngp.client.event.impl;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.client.impl.Loggers;
import org.camunda.tngp.client.task.impl.EventSubscriptions;
import org.camunda.tngp.client.task.impl.SubscribedEventHandler;
import org.camunda.tngp.util.DeferredCommandContext;
import org.slf4j.Logger;

public class EventAcquisition<T extends EventSubscription<T>> implements SubscribedEventHandler, Agent
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
    public String roleName()
    {
        return name;
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
            final Long subscriptionId = subscription.requestNewSubscription();

            subscription.setId(subscriptionId);
            subscriptions.addSubscription(subscription);
            future.complete(null);
        });
    }

    public void closeSubscription(T subscription)
    {
        subscription.requestSubscriptionClose();
        subscriptions.removeSubscription(subscription);
        subscription.onClose();
    }

    public CompletableFuture<Void> onEventsPolledAsync(T subscription, int numEvents)
    {
        return asyncContext.runAsync((future) ->
        {
            subscription.onEventsPolled(numEvents);
            future.complete(null);
        });
    }

    @Override
    public void onEvent(int topicId, long subscriptionId, TopicEventImpl event)
    {
        final T subscription = subscriptions.getSubscription(topicId, subscriptionId);

        if (subscription != null && subscription.isOpen())
        {
            subscription.addEvent(event);
        }
        else
        {
            LOGGER.debug(roleName() + ": Ignoring event " + event.toString() + " for subscription " + subscriptionId);
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
        }

        return workCount;
    }



}
