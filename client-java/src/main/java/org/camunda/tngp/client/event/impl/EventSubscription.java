package org.camunda.tngp.client.event.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.camunda.tngp.client.impl.Loggers;
import org.camunda.tngp.client.task.impl.EventSubscriptionCreationResult;
import org.camunda.tngp.util.CheckedConsumer;
import org.slf4j.Logger;

public abstract class EventSubscription<T extends EventSubscription<T>>
{
    // TODO: could become configurable in the future
    protected static final double REPLENISHMENT_THRESHOLD = 0.3d;

    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    public static final int STATE_NEW = 0;
    public static final int STATE_OPENING = 1;
    public static final int STATE_OPEN = 2;

    // semantics of closing: subscription is currently open on broker-side and we want to close it and clean up on client side
    public static final int STATE_CLOSING = 3;
    public static final int STATE_CLOSED = 4;

    // semantics of aborting: subscription is closed on broker side and we want to clean up on client side
    public static final int STATE_ABORTING = 5;
    public static final int STATE_ABORTED = 6;

    protected long subscriberKey;
    protected final EventAcquisition<T> eventAcquisition;
    protected final ManyToManyConcurrentArrayQueue<TopicEventImpl> pendingEvents;
    protected final int capacity;

    /*
     * The channel that events are received on
     */
    protected int receiveChannelId;

    protected final AtomicInteger state = new AtomicInteger(STATE_NEW);

    protected final AtomicInteger eventsInProcessing = new AtomicInteger(0);
    protected final AtomicInteger eventsProcessedSinceLastReplenishment = new AtomicInteger(0);
    protected CompletableFuture<Void> closeFuture;

    public EventSubscription(EventAcquisition<T> eventAcquisition, int capacity)
    {
        this.eventAcquisition = eventAcquisition;
        this.pendingEvents = new ManyToManyConcurrentArrayQueue<>(capacity);
        this.capacity = capacity;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    public void setSubscriberKey(long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
    }

    public int getReceiveChannelId()
    {
        return receiveChannelId;
    }

    public void setReceiveChannelId(int receiveChannelId)
    {
        this.receiveChannelId = receiveChannelId;
    }

    public int capacity()
    {
        return capacity;
    }

    public int size()
    {
        return pendingEvents.size();
    }

    public boolean hasPendingEvents()
    {
        return !pendingEvents.isEmpty();
    }

    public boolean hasEventsInProcessing()
    {
        return eventsInProcessing.get() > 0;
    }

    public boolean isOpen()
    {
        return state.get() == STATE_OPEN;
    }

    public boolean isClosing()
    {
        return state.get() == STATE_CLOSING;
    }

    public boolean isClosed()
    {
        final int currentState = state.get();
        return currentState == STATE_CLOSED || currentState == STATE_ABORTED;
    }

    public abstract boolean isManagedSubscription();

    public void close()
    {
        try
        {
            closeAsync().get();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Exception while closing subscription", e);
        }
    }

    public CompletableFuture<Void> closeAsync()
    {
        if (state.compareAndSet(STATE_OPEN, STATE_CLOSING))
        {
            closeFuture = new CompletableFuture<>();
            return closeFuture;
        }
        else
        {
            return CompletableFuture.completedFuture(null);
        }
    }

    public void open()
    {
        try
        {
            openAsync().get();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Exception while opening subscription", e);
        }
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> openAsync()
    {
        if (state.compareAndSet(STATE_NEW, STATE_OPENING))
        {
            return eventAcquisition
                .openSubscriptionAsync((T) this)
                .thenAccept((v) -> state.compareAndSet(STATE_OPENING, STATE_OPEN));

        }
        else
        {
            return CompletableFuture.completedFuture(null);
        }
    }

    public void abortAsync()
    {
        if (state.compareAndSet(STATE_OPEN, STATE_ABORTING))
        {
            eventAcquisition.abort((T) this);
        }
    }

    public void addEvent(TopicEventImpl event)
    {
        final boolean added = this.pendingEvents.offer(event);

        if (!added)
        {
            throw new RuntimeException("Cannot add any more events. Event queue saturated.");
        }
    }

    public abstract int poll();

    protected int pollEvents(CheckedConsumer<TopicEventImpl> pollHandler)
    {
        final int currentlyAvailableEvents = size();
        int handledEvents = 0;

        TopicEventImpl event;

        // handledTasks < currentlyAvailableTasks avoids very long cycles that we spend in this method
        // in case the broker continuously produces new tasks
        while (handledEvents < currentlyAvailableEvents)
        {
            event = pendingEvents.poll();
            if (event == null)
            {
                break;
            }

            eventsInProcessing.incrementAndGet();
            try
            {
                // Must first increment eventsInProcessing and only then check if the subscription
                // is still open. This avoids a race condition between the event handler executor
                // and the event acquisition checking if there are events in processing before closing a
                // subscription
                if (!isOpen())
                {
                    break;
                }

                handledEvents++;

                try
                {
                    pollHandler.accept(event);
                }
                catch (Exception e)
                {
                    onUnhandledEventHandlingException(event, e);
                }
            }
            finally
            {
                eventsInProcessing.decrementAndGet();
                eventsProcessedSinceLastReplenishment.incrementAndGet();
            }
        }

        return handledEvents;
    }

    protected void onUnhandledEventHandlingException(TopicEventImpl event, Exception e)
    {
        throw new RuntimeException("Exception during handling of event " + event.getEventKey(), e);
    }

    public void onClose()
    {
        state.compareAndSet(STATE_CLOSING, STATE_CLOSED);
        closeFuture.complete(null);
    }

    public void onCloseFailed(Exception e)
    {
        // setting this to closed anyway for now
        state.compareAndSet(STATE_CLOSING, STATE_CLOSED);
        closeFuture.completeExceptionally(e);
    }

    public void onAbort()
    {
        state.compareAndSet(STATE_ABORTING, STATE_ABORTED);
    }

    public void replenishEventSource()
    {
        final int eventsProcessed = eventsProcessedSinceLastReplenishment.get();
        final int remainingCapacity = capacity - eventsProcessed;

        if (remainingCapacity < capacity * REPLENISHMENT_THRESHOLD)
        {
            requestEventSourceReplenishment(eventsProcessed);
            eventsProcessedSinceLastReplenishment.addAndGet(-eventsProcessed);
        }
    }

    protected abstract void requestEventSourceReplenishment(int eventsProcessed);

    protected abstract EventSubscriptionCreationResult requestNewSubscription();

    protected abstract void requestSubscriptionClose();

    public abstract int getTopicId();


}
