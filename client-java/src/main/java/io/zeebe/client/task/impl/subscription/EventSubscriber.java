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

import java.util.concurrent.Future;
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

public abstract class EventSubscriber
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;
    protected static final String LOG_MESSAGE_PREFIX = "Subscriber {}: ";

    // TODO: could become configurable in the future
    protected static final double REPLENISHMENT_THRESHOLD = 0.3d;

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_REOPEN = 2;
    protected static final int TRANSITION_ABORT = 3;
    protected static final int TRANSITION_CLOSE = 4;

    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final ClosingState closingState = new ClosingState();
    protected final ClosedState closedState = new ClosedState();

    private final StateMachine<SimpleStateMachineContext> stateMachine = StateMachine.<SimpleStateMachineContext>builder((s) -> new SimpleStateMachineContext(s))
        .initialState(openingState)

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
    protected final EventAcquisition acquisition;

    protected RemoteAddress eventSource;
    protected int partitionId;

    protected final AtomicInteger eventsInProcessing = new AtomicInteger(0);
    protected final AtomicInteger eventsProcessedSinceLastReplenishment = new AtomicInteger(0);

    public EventSubscriber(int partitionId, int capacity, EventAcquisition acquisition)
    {
        this.pendingEvents = new ManyToManyConcurrentArrayQueue<>(capacity);
        this.capacity = capacity;
        this.acquisition = acquisition;
        this.partitionId = partitionId;
    }

    public int maintainState()
    {
        return stateMachineAgent.doWork();
    }

    class OpeningState implements State<SimpleStateMachineContext>
    {
        protected Future<? extends EventSubscriptionCreationResult> subscriptionFuture;

        @Override
        public boolean isInterruptable()
        {
            // this must be non-interruptable or else there is a potential race conditions between
            //   * the success response arriving
            //   * closing the subscriber from the outside (e.g. the subscriber group is closed during creation)
            //
            // Then we must ensure that we send a close request in any case to the broker so that
            // there is no lingering subscription on broker side.

            return false;
        }

        @Override
        public void onExit()
        {
            subscriptionFuture = null;
        }

        @Override
        public int doWork(SimpleStateMachineContext context) throws Exception
        {
            if (subscriptionFuture == null)
            {
                LOGGER.debug(LOG_MESSAGE_PREFIX + "Opening", EventSubscriber.this);
                subscriptionFuture = requestNewSubscription();

                return 1;
            }
            else if (subscriptionFuture.isDone())
            {
                final EventSubscriptionCreationResult result;

                try
                {
                    result = subscriptionFuture.get();
                }
                catch (Exception e)
                {
                    LOGGER.error("Subscriber {}; Could not open subscriber remotely. Aborting", EventSubscriber.this, e);
                    context.take(TRANSITION_ABORT);
                    return 1;
                }

                LOGGER.debug("Subscriber {} opened", EventSubscriber.this);

                subscriberKey = result.getSubscriberKey();
                partitionId = result.getPartitionId();
                eventSource = result.getEventPublisher();
                resetProcessingState();
                acquisition.activateSubscriber(EventSubscriber.this);

                context.take(TRANSITION_DEFAULT);

                return 1;
            }
            else
            {
                return 0;
            }
        }
    }

    class OpenState implements State<SimpleStateMachineContext>
    {
        @Override
        public int doWork(SimpleStateMachineContext context) throws Exception
        {
            // TODO: handle errors => https://github.com/zeebe-io/zeebe/issues/591
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
                    LOGGER.warn(LOG_MESSAGE_PREFIX + "Exception when closing subscription", EventSubscriber.this, e);
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
        public void onEnter(SimpleStateMachineContext context)
        {
            acquisition.deactivateSubscriber(EventSubscriber.this);
        }

        @Override
        public void work(SimpleStateMachineContext context) throws Exception
        {
        }
    }

    public RemoteAddress getEventSource()
    {
        return eventSource;
    }

    public void closeAsync()
    {
        isCloseIssued.set(true);

        if (!isClosed())
        {
            stateMachineAgent.addCommand(s ->
            {
                s.tryTake(TRANSITION_CLOSE);
            });
        }

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

    public boolean isOpening()
    {
        return stateMachine.isInState(openingState);
    }

    public boolean isClosed()
    {
        return stateMachine.isInState(closedState);
    }

    public int size()
    {
        return pendingEvents.size();
    }

    protected boolean replenishEventSource()
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
            LOGGER.warn(LOG_MESSAGE_PREFIX + "Cannot add any more events. Event queue saturated. Postponing event {}.",
                    this, event);
        }

        return added;
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
            LOGGER.trace(LOG_MESSAGE_PREFIX + "Handling event {}", this, event);
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

    public abstract String getTopicName();

    public int getPartitionId()
    {
        return partitionId;
    }

    protected abstract Future<? extends EventSubscriptionCreationResult> requestNewSubscription();
    protected abstract void requestSubscriptionClose();
}
