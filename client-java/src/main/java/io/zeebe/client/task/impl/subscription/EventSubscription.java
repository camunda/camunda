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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.slf4j.Logger;

import io.zeebe.client.event.impl.GeneralEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.WaitState;

public abstract class EventSubscription<T extends EventSubscription<T>>
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    // TODO: could become configurable in the future
    protected static final double REPLENISHMENT_THRESHOLD = 0.3d;

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_REOPEN = 2;
    protected static final int TRANSITION_ABORT = 3;
    protected static final int TRANSITION_CLOSE = 4;

    protected final InitState initState = new InitState();
    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final ClosingState closingState = new ClosingState();
    protected final ClosedState closedState = new ClosedState();

    private final StateMachine<SimpleStateMachineContext> stateMachine = StateMachine.<SimpleStateMachineContext>builder((s) -> new SimpleStateMachineContext(s))
        .initialState(initState)
        .from(initState).take(TRANSITION_OPEN).to(openingState)

        .from(openingState).take(TRANSITION_DEFAULT).to(openState)
        .from(openingState).take(TRANSITION_ABORT).to(closedState)

        .from(openState).take(TRANSITION_REOPEN).to(openingState)
        .from(openState).take(TRANSITION_ABORT).to(closedState)

        .from(openState).take(TRANSITION_CLOSE).to(closingState)

        .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
        .from(closingState).take(TRANSITION_CLOSE).to(closedState)

        .from(closedState).take(TRANSITION_CLOSE).to(closedState)

        .build();

    private StateMachineAgent<SimpleStateMachineContext> stateMachineAgent = new StateMachineAgent<>(stateMachine);

    // at some points we need to know immediately that a close request has been issued (not only
    // when the state machine asynchronously processes it)
    protected AtomicBoolean isCloseIssued = new AtomicBoolean(false);

    protected long subscriberKey;
    protected final ManyToManyConcurrentArrayQueue<GeneralEventImpl> pendingEvents;
    protected final int capacity;
    protected final EventAcquisition<T> acquisition;

    protected RemoteAddress eventSource;
    protected final String topic;
    protected final int partitionId;

    protected final AtomicInteger eventsInProcessing = new AtomicInteger(0);
    protected final AtomicInteger eventsProcessedSinceLastReplenishment = new AtomicInteger(0);

    protected CompletableFuture<T> openFuture;
    protected CompletableFuture<T> closeFuture;

    public EventSubscription(String topic, int partitionId, int capacity, EventAcquisition<T> acquisition)
    {
        this.pendingEvents = new ManyToManyConcurrentArrayQueue<>(capacity);
        this.capacity = capacity;
        this.acquisition = acquisition;
        this.topic = topic;
        this.partitionId = partitionId;
    }

    public int maintainState()
    {
        return stateMachineAgent.doWork();
    }

    class InitState implements WaitState<SimpleStateMachineContext>
    {
        @Override
        public void work(SimpleStateMachineContext context) throws Exception
        {
            // wait for open command
        }
    }

    class OpeningState implements State<SimpleStateMachineContext>
    {
        @Override
        public int doWork(SimpleStateMachineContext context) throws Exception
        {
            final EventSubscriptionCreationResult result;

            // TODO: can become non-blocking
            try
            {
                result = requestNewSubscription();
            }
            catch (Exception e)
            {
                // TODO: this exception should probably be used as a cause for cancelling a pending
                //   opening future
                LOGGER.info("Could not open subscription; aborting", e);
                context.take(TRANSITION_ABORT);
                return 1;
            }

            subscriberKey = result.getSubscriberKey();
            eventSource = result.getEventPublisher();
            resetProcessingState();
            acquisition.activateSubscription(thisSubscription());

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

    }

    class OpenState implements State<SimpleStateMachineContext>
    {
        @Override
        public int doWork(SimpleStateMachineContext context) throws Exception
        {
            if (openFuture != null)
            {
                openFuture.complete(thisSubscription());
                openFuture = null;
            }

            final boolean replenished = replenishEventSource();

            if (replenished)
            {
                return 1;
            }
            else
            {
                return 0;
            }

            // relax and wait for external commands
        }
    }


    class ClosingState implements State<SimpleStateMachineContext>
    {
        @Override
        public int doWork(SimpleStateMachineContext context) throws Exception
        {
            if (!hasEventsInProcessing())
            {
                try
                {
                    // TODO: can become non-blocking
                    requestSubscriptionClose();
                }
                catch (Exception e)
                {
                    LOGGER.warn("Exception when closing subscription", e);
                }

                context.take(TRANSITION_DEFAULT);
                return 1;
            }
            else
            {
                return 0;
            }

        }
    }

    class ClosedState implements WaitState<SimpleStateMachineContext>
    {
        @Override
        public void work(SimpleStateMachineContext context) throws Exception
        {
            if (openFuture != null)
            {
                openFuture.cancel(true);
                openFuture = null;
            }
            if (closeFuture != null)
            {
                closeFuture.complete(thisSubscription());
                closeFuture = null;
            }

            acquisition.stopManageSubscription(thisSubscription());
        }
    }

    public RemoteAddress getEventSource()
    {
        return eventSource;
    }

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
        isCloseIssued.set(true);
        final CompletableFuture<T> closeFuture = new CompletableFuture<>();

        if (isClosed())
        {
            // if closed, the state machine is no longer managed, so state transitions won't be picked up
            closeFuture.complete(thisSubscription());
            return closeFuture;
        }

        stateMachineAgent.addCommand(s ->
        {
            final boolean success = s.tryTake(TRANSITION_CLOSE);
            if (success)
            {
                if (this.closeFuture == null)
                {
                    this.closeFuture = closeFuture;
                }
                else
                {
                    this.closeFuture.whenComplete((v, t) ->
                    {
                        if (t == null)
                        {
                            closeFuture.complete(v);
                        }
                        else
                        {
                            closeFuture.completeExceptionally(t);
                        }
                    });
                }
            }
            else
            {
                closeFuture.cancel(true);
            }


        });

        return closeFuture;
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
        stateMachineAgent.addCommand(s ->
        {
            final boolean success = s.tryTake(TRANSITION_OPEN);
            if (success)
            {
                this.openFuture = openFuture;
            }
            else
            {
                openFuture.cancel(true);
            }
        });

        return openFuture;
    }

    public void reopenAsync()
    {
        stateMachineAgent.addCommand(s ->
        {
            s.tryTake(TRANSITION_REOPEN);
        });
    }

    public boolean isOpen()
    {
        return stateMachine.isInState(openState);
    }

    public boolean isClosed()
    {
        return stateMachine.isInState(closedState);
    }

    public int size()
    {
        return pendingEvents.size();
    }

    public boolean replenishEventSource()
    {
        final int eventsProcessed = eventsProcessedSinceLastReplenishment.get();
        final int remainingCapacity = capacity - eventsProcessed;

        final boolean requestReplenishment = remainingCapacity < capacity * REPLENISHMENT_THRESHOLD;

        if (requestReplenishment)
        {
            requestEventSourceReplenishment(eventsProcessed);
            eventsProcessedSinceLastReplenishment.addAndGet(-eventsProcessed);
        }

        return requestReplenishment;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    protected abstract void requestEventSourceReplenishment(int eventsProcessed);

    public boolean addEvent(GeneralEventImpl event)
    {
        final boolean added = this.pendingEvents.offer(event);

        if (!added)
        {
            LOGGER.warn("Cannot add any more events. Event queue saturated. Postponing event.");
        }

        return added;
    }

    @SuppressWarnings("unchecked")
    protected T thisSubscription()
    {
        return (T) this;
    }

    protected void resetProcessingState()
    {
        pendingEvents.clear();
        eventsInProcessing.set(0);
        eventsProcessedSinceLastReplenishment.set(0);
    }

    protected boolean hasEventsInProcessing()
    {
        return eventsInProcessing.get() > 0;
    }

    protected int pollEvents(CheckedConsumer<GeneralEventImpl> pollHandler)
    {
        final int currentlyAvailableEvents = size();
        int handledEvents = 0;

        GeneralEventImpl event;

        // handledTasks < currentlyAvailableTasks avoids very long cycles that we spend in this method
        // in case the broker continuously produces new tasks
        while (handledEvents < currentlyAvailableEvents && isOpen() && !isCloseIssued.get())
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

    protected void logHandling(GeneralEventImpl event)
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

    protected void onUnhandledEventHandlingException(GeneralEventImpl event, Exception e)
    {
        throw new RuntimeException("Exception during handling of event " + event.getMetadata().getKey(), e);
    }

    public String getTopicName()
    {
        return topic;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    protected abstract EventSubscriptionCreationResult requestNewSubscription();
    protected abstract void requestSubscriptionClose();
    public abstract boolean isManagedSubscription();
    public abstract int poll();

}
