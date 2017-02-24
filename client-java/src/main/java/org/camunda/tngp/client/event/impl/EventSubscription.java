package org.camunda.tngp.client.event.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.camunda.tngp.client.impl.Loggers;
import org.camunda.tngp.util.CheckedConsumer;
import org.slf4j.Logger;

public abstract class EventSubscription<T extends EventSubscription<T>>
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    public static final int STATE_NEW = 0;
    public static final int STATE_OPENING = 1;
    public static final int STATE_OPEN = 2;
    public static final int STATE_CLOSING = 3;
    public static final int STATE_CLOSED = 4;

    protected long id;
    protected final EventAcquisition<T> eventAcquisition;
    protected final ManyToManyConcurrentArrayQueue<TopicEventImpl> pendingEvents;
    protected final int capacity;

    protected final AtomicInteger state = new AtomicInteger(STATE_NEW);
    protected CompletableFuture<Void> closeFuture;

    public EventSubscription(EventAcquisition<T> eventAcquisition, int upperBoundCapacity)
    {
        this.eventAcquisition = eventAcquisition;
        this.pendingEvents = new ManyToManyConcurrentArrayQueue<>(upperBoundCapacity);
        this.capacity = pendingEvents.capacity();
    }

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
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
        return state.get() == STATE_CLOSED;
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

    public void addEvent(TopicEventImpl event)
    {
        final boolean added = this.pendingEvents.offer(event);

        if (!added)
        {
            throw new RuntimeException("Cannot add any more events. Event queue saturated.");
        }
    }

    public abstract int poll();

    public int pollEvents(CheckedConsumer<TopicEventImpl> pollHandler)
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

            handledEvents++;

            try
            {
                pollHandler.accept(event);
            }
            catch (Exception e)
            {
                onEventHandlingException(event, e);
            }
        }

        if (handledEvents > 0)
        {
            eventAcquisition.onEventsPolledAsync((T) this, handledEvents);
        }

        return handledEvents;
    }

    protected void onEventHandlingException(TopicEventImpl event, Exception e)
    {
        // could become configurable in the future (e.g. unlock task or report an error via API)
        LOGGER.error("Exception during handling of event " + event.getEventKey(), e);
    }

    public void onClose()
    {
        state.compareAndSet(STATE_CLOSING, STATE_CLOSED);
        closeFuture.complete(null);
    }

}
