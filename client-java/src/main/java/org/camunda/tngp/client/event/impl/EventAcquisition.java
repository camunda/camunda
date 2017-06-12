package org.camunda.tngp.client.event.impl;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.camunda.tngp.client.impl.Loggers;
import org.camunda.tngp.client.task.impl.subscription.EventSubscriptionCreationResult;
import org.camunda.tngp.client.task.impl.subscription.EventSubscriptions;
import org.camunda.tngp.client.task.impl.subscription.SubscribedEventHandler;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.actor.Actor;
import org.camunda.tngp.util.state.concurrent.SharedStateMachine;
import org.camunda.tngp.util.state.concurrent.SharedStateMachineBlueprint;
import org.camunda.tngp.util.state.concurrent.SharedStateMachineManager;
import org.slf4j.Logger;

public class EventAcquisition<T extends EventSubscription<T>> implements SubscribedEventHandler, Actor
{

    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;
    protected static final int STATE_BUFFER_SIZE = 1024 * 1024 * 2;

    protected final EventSubscriptions<T> subscriptions;
    protected DeferredCommandContext asyncContext;
    protected String name;

    protected final SharedStateMachineBlueprint<T> subscriptionLifecycle;
    protected final SharedStateMachineManager<T> stateMachineManager;

    public EventAcquisition(String name, EventSubscriptions<T> subscriptions)
    {
        this.name = name;
        this.asyncContext = new DeferredCommandContext();
        this.subscriptions = subscriptions;
        this.subscriptionLifecycle = new SharedStateMachineBlueprint<T>()
                .onState(EventSubscription.STATE_OPENING, this::openSubscription)
                .onState(EventSubscription.STATE_CLOSED, this::removeSubscription)
                .onState(EventSubscription.STATE_ABORTING, this::abortSubscription)
                .onState(EventSubscription.STATE_SUSPENDED,  s -> subscriptions.onSubscriptionClosed(s))
                .onState(EventSubscription.STATE_ABORTED, this::removeSubscription);

        final ManyToOneRingBuffer stateChangeBuffer = new ManyToOneRingBuffer(new UnsafeBuffer(new byte[STATE_BUFFER_SIZE + RingBufferDescriptor.TRAILER_LENGTH]));
        this.stateMachineManager = new SharedStateMachineManager<>(stateChangeBuffer);
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
        workCount += stateMachineManager.dispatchTransitionEvents();
        workCount += manageSubscriptions();

        return workCount;
    }

    public CompletableFuture<T> newSubscriptionAsync(T subscription)
    {
        return asyncContext.runAsync((future) ->
        {
            final SharedStateMachine<T> stateMachine = stateMachineManager.buildStateMachine(subscriptionLifecycle, subscription);
            stateMachineManager.register(stateMachine);
            subscriptions.addSubscription(subscription);
            subscription.initStateManagement(stateMachine);
            future.complete(subscription);
        });
    }

    protected void openSubscription(T subscription)
    {
        final EventSubscriptionCreationResult result;

        try
        {
            result = subscription.requestNewSubscription();
        }
        catch (Exception e)
        {
            abortSubscription(subscription);
            return;
        }

        subscription.setSubscriberKey(result.getSubscriberKey());
        subscription.setReceiveChannelId(result.getReceiveChannelId());
        subscription.onOpen();
        subscriptions.onSubscriptionOpened(subscription);
    }

    protected void removeSubscription(T subscription)
    {
        subscriptions.removeSubscription(subscription);
        stateMachineManager.unregister(subscription.getStateMachine());
    }

    protected void closeSubscription(T subscription)
    {
        try
        {
            subscription.requestSubscriptionClose();
            subscription.onClose();
        }
        catch (Exception e)
        {
            LOGGER.warn("Exception when closing subscription", e);
            subscription.onCloseFailed(e);
        }
    }

    protected void abortSubscription(T subscription)
    {
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
