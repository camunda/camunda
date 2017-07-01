package io.zeebe.client.event.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.transport.Channel;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.state.concurrent.SharedStateMachine;
import org.slf4j.Logger;

public abstract class EventSubscription<T extends EventSubscription<T>>
{
    // TODO: could become configurable in the future
    protected static final double REPLENISHMENT_THRESHOLD = 0.3d;

    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    public static final int STATE_NEW = 1 << 0;
    public static final int STATE_OPENING = 1 << 1;
    public static final int STATE_OPEN = 1 << 2;

    // semantics of closing: subscription is currently open on broker-side and we want to close it and clean up on client side
    public static final int STATE_CLOSING = 1 << 3;
    public static final int STATE_CLOSED = 1 << 4; // terminal state

    // semantics of aborting: subscription is closed on broker side and we want to clean up on client side
    public static final int STATE_ABORTING = 1 << 5;
    public static final int STATE_ABORTED = 1 << 6; // terminal state

    // subscription is closed on broker side, but can be reopened later
    public static final int STATE_SUSPENDED = 1 << 7;

    protected long subscriberKey;
    protected final ManyToManyConcurrentArrayQueue<TopicEventImpl> pendingEvents;
    protected final int capacity;

    protected SharedStateMachine<T> stateMachine;

    /*
     * The channel that events are received on
     */
    protected Channel receiveChannel;

    protected final AtomicInteger eventsInProcessing = new AtomicInteger(0);
    protected final AtomicInteger eventsProcessedSinceLastReplenishment = new AtomicInteger(0);

    public EventSubscription(int capacity)
    {
        this.pendingEvents = new ManyToManyConcurrentArrayQueue<>(capacity);
        this.capacity = capacity;
    }

    public void initStateManagement(SharedStateMachine<T> stateMachine)
    {
        this.stateMachine = stateMachine;
        this.stateMachine.makeStateTransition(STATE_NEW);
    }

    public SharedStateMachine<T> getStateMachine()
    {
        return stateMachine;
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
        return receiveChannel != null ? receiveChannel.getStreamId() : -1;
    }

    public Channel getReceiveChannel()
    {
        return receiveChannel;
    }

    public void setReceiveChannel(Channel receiveChannel)
    {
        this.receiveChannel = receiveChannel;
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

    public void resetProcessingState()
    {
        pendingEvents.clear();
        eventsInProcessing.set(0);
        eventsProcessedSinceLastReplenishment.set(0);
    }

    public boolean isOpen()
    {
        return stateMachine.isInState(STATE_OPEN);
    }

    public boolean isClosing()
    {
        return stateMachine.isInAnyState(STATE_CLOSING);
    }

    public boolean isClosed()
    {
        return stateMachine.isInAnyState(STATE_CLOSED | STATE_ABORTED);
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

    public CompletableFuture<T> closeAsync()
    {
        final CompletableFuture<T> closeFuture = new CompletableFuture<>();
        if (stateMachine.makeStateTransitionAndDo(STATE_OPEN, STATE_CLOSING, (s) -> s.listenFor(STATE_CLOSED | STATE_ABORTED, 0, closeFuture)))
        {
            return closeFuture;
        }
        else
        {
            throw new RuntimeException("Could not close subscription");
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

    public CompletableFuture<T> openAsync()
    {
        final CompletableFuture<T> openFuture = new CompletableFuture<>();
        if (stateMachine.makeStateTransitionAndDo(STATE_NEW, STATE_OPENING, (s) -> s.listenFor(STATE_OPEN, STATE_CLOSED | STATE_ABORTED, openFuture)))
        {
            return openFuture;
        }
        else
        {
            throw new RuntimeException("Could not open channel");
        }
    }

    public boolean reopenAsync()
    {
        return stateMachine.makeStateTransition(STATE_SUSPENDED, STATE_OPENING);
    }

    public void abortAsync()
    {
        stateMachine.makeStateTransition(STATE_ABORTING);
    }

    public void suspendAsync()
    {
        stateMachine.makeStateTransition(STATE_SUSPENDED);
    }


    public boolean addEvent(TopicEventImpl event)
    {
        final boolean added = this.pendingEvents.offer(event);

        if (!added)
        {
            LOGGER.warn("Cannot add any more events. Event queue saturated. Postponing event.");
        }

        return added;
    }

    public abstract int poll();

    protected int pollEvents(CheckedConsumer<TopicEventImpl> pollHandler)
    {
        final int currentlyAvailableEvents = size();
        int handledEvents = 0;

        TopicEventImpl event;

        // handledTasks < currentlyAvailableTasks avoids very long cycles that we spend in this method
        // in case the broker continuously produces new tasks
        while (handledEvents < currentlyAvailableEvents && isOpen())
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
                logHandling(event);

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

    protected void logHandling(TopicEventImpl event)
    {
        try
        {
            LOGGER.debug("{} handling event {}", this, event);
        }
        catch (Exception e)
        {
            // serializing the event might fail (involves msgpack to JSON conversion)
            LOGGER.warn("Could not construct or write log message", e);
        }
    }

    protected void onUnhandledEventHandlingException(TopicEventImpl event, Exception e)
    {
        throw new RuntimeException("Exception during handling of event " + event.getEventKey(), e);
    }

    public void onClose()
    {
        stateMachine.makeStateTransition(STATE_CLOSED);
    }

    public void onCloseFailed(Exception e)
    {
        stateMachine.makeStateTransition(STATE_CLOSED);
    }

    public void onAbort()
    {
        stateMachine.makeStateTransition(STATE_ABORTED);
    }

    public void onOpen()
    {
        if (stateMachine.makeStateTransition(STATE_OPENING, STATE_OPEN))
        {
            resetProcessingState(); // in case subscription is reopened
        }

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

    public abstract String getTopicName();

    public abstract int getPartitionId();


}
