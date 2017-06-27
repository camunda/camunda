package io.zeebe.client.task.impl.subscription;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

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

    public void onSubscriptionOpened(T subscription)
    {
        // TODO: rename method to something like exposeSubscription() ?!
        this.subscriptions.onSubscriptionOpened(subscription);
    }

    public void onSubscriptionTerminated(T subscription)
    {
        // TODO: rename method to something like disposeSubscription() ?!
        // TODO: could also become one call
        this.subscriptions.onSubscriptionClosed(subscription);
        this.subscriptions.removeSubscription(subscription);
    }

    // TODO: rename method
    public CompletableFuture<T> newSubscriptionAsync(T subscription)
    {
        return asyncContext.runAsync((future) ->
        {
            subscriptions.addSubscription(subscription);
        });
    }

}
