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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.slf4j.Logger;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.impl.GeneralEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.topic.Partition;
import io.zeebe.client.topic.Topic;
import io.zeebe.client.topic.Topics;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.WaitState;

public abstract class EventSubscriberGroup<T extends EventSubscriber>
{
    protected static final Logger LOG = Loggers.SUBSCRIPTION_LOGGER;
    protected static final String LOG_MESSAGE_PREFIX = "Subscriber Group {}: ";

    protected List<Partition> partitions;
    protected List<T> subscribers = new ArrayList<>();

    protected CompletableFuture<EventSubscriberGroup<T>> openFuture;
    protected CompletableFuture<EventSubscriberGroup<T>> closeFuture;

    protected final InitState initState = new InitState();
    protected final DeterminePartitionsState determinePartitionsState = new DeterminePartitionsState();
    protected final InitiateSubscribersState initSubscribers = new InitiateSubscribersState();
    protected final OpeningState openingState = new OpeningState();
    protected final OpenedState openedState = new OpenedState();
    protected final InitiateCloseState initCloseState = new InitiateCloseState();
    protected final ClosingState closingState = new ClosingState();
    protected final ClosedState closedState = new ClosedState();

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;

    protected final EventAcquisition acquisition;
    protected final ZeebeClient client;
    protected final String topic;

    private final StateMachine<GroupContext> stateMachine = StateMachine.<GroupContext>builder((s) -> new GroupContext(s))
        .initialState(initState)
        .from(initState).take(TRANSITION_OPEN).to(determinePartitionsState)

        .from(determinePartitionsState).take(TRANSITION_DEFAULT).to(initSubscribers)
        .from(determinePartitionsState).take(TRANSITION_CLOSE).to(closedState)

        .from(initSubscribers).take(TRANSITION_DEFAULT).to(openingState)

        .from(openingState).take(TRANSITION_DEFAULT).to(openedState)
        .from(openingState).take(TRANSITION_CLOSE).to(initCloseState)

        .from(openedState).take(TRANSITION_CLOSE).to(initCloseState)

        .from(initCloseState).take(TRANSITION_DEFAULT).to(closingState)
        .from(initCloseState).take(TRANSITION_CLOSE).to(initCloseState)

        .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
        .from(closingState).take(TRANSITION_CLOSE).to(closingState)

        .from(closedState).take(TRANSITION_CLOSE).to(closedState)

        .build();


    private StateMachineAgent<GroupContext> stateMachineAgent = new StateMachineAgent<>(stateMachine);

    public EventSubscriberGroup(EventAcquisition acquisition, ZeebeClient client, String topic)
    {
        this.acquisition = acquisition;
        this.client = client;
        this.topic = topic;
        acquisition.registerSubscriptionAsync(this);
    }

    class InitState implements WaitState<GroupContext>
    {

        @Override
        public void work(GroupContext context) throws Exception
        {
            // wait for open command
        }
    }

    class DeterminePartitionsState implements State<GroupContext>
    {

        protected Future<Topics> topicsFuture;

        @Override
        public int doWork(GroupContext context) throws Exception
        {
            if (topicsFuture == null)
            {
                requestTopics(context);
                return 1;
            }
            else
            {
                return determinePartitions(context);
            }
        }

        @Override
        public void onExit()
        {
            topicsFuture = null;
        }

        private void requestTopics(GroupContext context)
        {
            Loggers.SUBSCRIPTION_LOGGER.debug(LOG_MESSAGE_PREFIX + "Determining partitions of topic", EventSubscriberGroup.this);
            topicsFuture = client.topics().getTopics().executeAsync();
        }

        private int determinePartitions(GroupContext context)
        {
            if (topicsFuture.isDone())
            {
                final Topics topics;
                try
                {
                    topics = topicsFuture.get();
                }
                catch (Exception e)
                {
                    final String error = "Could not fetch topics";
                    LOG.error(LOG_MESSAGE_PREFIX + error, EventSubscriberGroup.this, e);
                    context.setCloseReason(error);

                    context.take(TRANSITION_CLOSE);

                    return 1;
                }

                final Optional<Topic> requestedTopic =
                    topics.getTopics()
                        .stream()
                        .filter(t -> topic.equals(t.getName()))
                        .findFirst();

                if (requestedTopic.isPresent())
                {
                    partitions = requestedTopic.get().getPartitions();
                    context.take(TRANSITION_DEFAULT);
                }
                else
                {
                    context.setCloseReason(String.format("Topic %s is not known", topic));

                    context.take(TRANSITION_CLOSE);
                }

                return 1;
            }
            else
            {
                return 0;
            }
        }
    }

    protected abstract T buildSubscriber(int partition);

    class InitiateSubscribersState implements State<GroupContext>
    {

        @Override
        public int doWork(GroupContext context) throws Exception
        {
            LOG.debug(LOG_MESSAGE_PREFIX + "Subscribing to partitions {}", EventSubscriberGroup.this, partitions);
            for (Partition partition : partitions)
            {
                final T subscriber = buildSubscriber(partition.getId());
                subscribers.add(subscriber);
                acquisition.addSubscriber(subscriber);
            }

            context.take(TRANSITION_DEFAULT);
            return 1;
        }
    }

    class OpeningState implements State<GroupContext>
    {

        @Override
        public int doWork(GroupContext context) throws Exception
        {
            boolean allOpened = true;
            boolean anyClosed = false;

            for (EventSubscriber subscription : subscribers)
            {
                allOpened &= subscription.isOpen();
                anyClosed |= subscription.isClosed();
            }

            if (anyClosed)
            {
                context.setCloseReason("A subscriber closed unexpectedly.");
                LOG.error(LOG_MESSAGE_PREFIX + "Closing unexpectedly", EventSubscriberGroup.this);

                context.take(TRANSITION_CLOSE);
                return 1;
            }
            if (allOpened)
            {
                LOG.debug(LOG_MESSAGE_PREFIX + "All subscribers opened", EventSubscriberGroup.this);
                context.take(TRANSITION_DEFAULT);
                return 1;
            }

            return 0;
        }
    }

    class OpenedState implements State<GroupContext>
    {

        @Override
        public int doWork(GroupContext context) throws Exception
        {
            if (openFuture != null)
            {
                openFuture.complete(EventSubscriberGroup.this);
                openFuture = null;
            }

            boolean anyClosed = false;
            for (EventSubscriber subscription : subscribers)
            {
                anyClosed |= subscription.isClosed();
            }

            if (anyClosed)
            {
                context.setCloseReason("A subscriber closed unexpectedly.");
                LOG.error(LOG_MESSAGE_PREFIX + "Closing unexpectedly", EventSubscriberGroup.this);

                context.take(TRANSITION_CLOSE);
                return 1;
            }
            else
            {
                // relax and wait for external commands
                return 0;
            }
        }
    }

    class InitiateCloseState implements State<GroupContext>
    {

        @Override
        public int doWork(GroupContext context) throws Exception
        {
            for (EventSubscriber subscription : subscribers)
            {
                subscription.closeAsync();
            }

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

    }

    class ClosingState implements State<GroupContext>
    {
        @Override
        public int doWork(GroupContext context) throws Exception
        {
            boolean allClosed = true;
            for (EventSubscriber subscription : subscribers)
            {
                allClosed &= subscription.isClosed();
            }

            if (allClosed)
            {
                context.take(TRANSITION_DEFAULT);
                return 1;
            }
            else
            {
                return 0;
            }
        }

        @Override
        public void onExit()
        {
            for (EventSubscriber subscriber : subscribers)
            {
                acquisition.removeSubscriber(subscriber);
            }
        }
    }

    class ClosedState implements WaitState<GroupContext>
    {

        @Override
        public void work(GroupContext context) throws Exception
        {
            if (openFuture != null)
            {
                final String reason = String.format("Could not create subscriber group %s: %s", describeGroupSpec(), context.getCloseReason());
                openFuture.completeExceptionally(new ClientException(reason));
                openFuture = null;
            }
            if (closeFuture != null)
            {
                closeFuture.complete(EventSubscriberGroup.this);
                closeFuture = null;
            }

            acquisition.stopManageGroup(EventSubscriberGroup.this);
        }
    }

    public CompletableFuture<EventSubscriberGroup<T>> closeAsync()
    {
        final CompletableFuture<EventSubscriberGroup<T>> closeFuture = new CompletableFuture<>();

        if (isClosed())
        {
            // if closed, the state machine is no longer managed, so state transitions won't be picked up
            closeFuture.complete(this);
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

    public void close()
    {
        try
        {
            closeAsync().get();
        }
        catch (Exception e)
        {
            throw new ClientException("Exception while closing subscription", e);
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
            throw new ClientException("Could not open subscription: " + e.getMessage(), e);
        }
    }

    public CompletableFuture<EventSubscriberGroup<T>> openAsync()
    {
        final CompletableFuture<EventSubscriberGroup<T>> openFuture = new CompletableFuture<>();
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

    public boolean isOpen()
    {
        return stateMachine.isInState(openedState);
    }

    public boolean isOpening()
    {
        return stateMachine.isInState(openingState);
    }

    public boolean isClosed()
    {
        return stateMachine.isInState(closedState);
    }

    public int maintainState()
    {
        int workCount = stateMachineAgent.doWork();

        for (EventSubscriber subscription : subscribers)
        {
            workCount += subscription.maintainState();
        }

        return workCount;
    }

    public abstract boolean isManagedGroup();

    public void reopenSubscribersForRemote(RemoteAddress remoteAddress)
    {
        for (EventSubscriber subscription : subscribers)
        {
            // s.getEventSource is null if the subscription is not yet opened
            if (remoteAddress.equals(subscription.getEventSource()))
            {
                subscription.reopenAsync();
            }
        }
    }

    public abstract int poll();

    protected abstract String describeGroupSpec();

    public int pollEvents(CheckedConsumer<GeneralEventImpl> pollHandler)
    {
        int events = 0;
        for (EventSubscriber subscriber : subscribers)
        {
            events += subscriber.pollEvents(pollHandler);
        }

        return events;
    }

    public int size()
    {
        int events = 0;
        for (EventSubscriber subscriber : subscribers)
        {
            events += subscriber.size();
        }

        return events;
    }

    /**
     * exposing internal state; for testing only
     */
    public List<T> getSubscribers()
    {
        final CompletableFuture<List<T>> result = new CompletableFuture<>();
        stateMachineAgent.addCommand(c ->
        {
            result.complete(new ArrayList<>(subscribers));
        });

        try
        {
            return result.get();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString()
    {
        return describeGroupSpec();
    }

    protected static class GroupContext extends SimpleStateMachineContext
    {
        private String closeReason;

        public GroupContext(StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        public void setCloseReason(String closeReason)
        {
            this.closeReason = closeReason;
        }

        public String getCloseReason()
        {
            return closeReason;
        }

    }


}
