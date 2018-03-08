/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.task.processor;

import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static io.zeebe.util.EnsureUtil.*;

import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.logstreams.processor.NoopSnapshotSupport;
import io.zeebe.broker.task.*;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.task.processor.TaskSubscriptions.SubscriptionIterator;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.*;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

public class LockTaskStreamProcessor implements StreamProcessor, EventProcessor
{
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final NoopSnapshotSupport noopSnapshotSupport = new NoopSnapshotSupport();
    protected CreditsRequestBuffer creditsBuffer = new CreditsRequestBuffer(TaskSubscriptionManager.NUM_CONCURRENT_REQUESTS, this::increaseSubscriptionCredits);

    protected final TaskSubscriptions subscriptions = new TaskSubscriptions(8);
    protected final SubscriptionIterator taskDistributionIterator;
    protected final SubscriptionIterator managementIterator;

    protected final DirectBuffer subscribedTaskType;

    protected int logStreamPartitionId;

    protected LogStream targetStream;

    protected final TaskEvent taskEvent = new TaskEvent();
    protected long eventKey = 0;

    protected boolean hasLockedTask;
    protected TaskSubscription lockSubscription;


    private ActorControl actor;
    private StreamProcessorContext context;

    public LockTaskStreamProcessor(DirectBuffer taskType)
    {
        this.subscribedTaskType = taskType;
        this.taskDistributionIterator = subscriptions.iterator();
        this.managementIterator = subscriptions.iterator();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        // need to restore the log position
        return noopSnapshotSupport;
    }

    public DirectBuffer getSubscriptedTaskType()
    {
        return subscribedTaskType;
    }

    public int getLogStreamPartitionId()
    {
        return logStreamPartitionId;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        creditsBuffer.handleRequests();
        this.context = context;
        final LogStream logStream = context.getLogStream();
        logStreamPartitionId = logStream.getPartitionId();

        targetStream = logStream;
        actor = context.getActorControl();

        // activate the processor while adding the first subscription
        context.suspendController();
    }

    public ActorFuture<Void> addSubscription(TaskSubscription subscription)
    {
        ensureNotNull("subscription", subscription);
        ensureNotNull("lock task type", subscription.getLockTaskType());
        ensureNotNull("lock owner", subscription.getLockOwner());
        ensureGreaterThan("length of lock owner", subscription.getLockOwner().capacity(), 0);
        ensureLessThanOrEqual("length of lock owner", subscription.getLockOwner().capacity(), TaskSubscription.LOCK_OWNER_MAX_LENGTH);
        ensureGreaterThan("lock duration", subscription.getLockDuration(), 0);
        ensureGreaterThan("subscription credits", subscription.getCredits(), 0);

        if (!BufferUtil.equals(subscription.getLockTaskType(), subscribedTaskType))
        {
            final String errorMessage = String.format("Subscription task type is not equal to '%s'.", BufferUtil.bufferAsString(subscribedTaskType));
            throw new RuntimeException(errorMessage);
        }

        return actor.call(() ->
        {
            subscriptions.addSubscription(subscription);

            context.resumeController();
        });
    }

    public ActorFuture<Boolean> removeSubscription(long subscriberKey)
    {
        final CompletableActorFuture<Boolean> completableFuture = new CompletableActorFuture<>();
        actor.call(() ->
        {
            subscriptions.removeSubscription(subscriberKey);
            final boolean isSuspended = subscriptions.isEmpty();
            if (isSuspended)
            {
                context.suspendController();
            }

            completableFuture.complete(!isSuspended);
        });
        return completableFuture;
    }

    public ActorFuture<Boolean> onClientChannelCloseAsync(int channelId)
    {
        final CompletableActorFuture<Boolean> completableFuture = new CompletableActorFuture<>();
        actor.call(() ->
        {
            managementIterator.reset();

            while (managementIterator.hasNext())
            {
                final TaskSubscription subscription = managementIterator.next();
                if (subscription.getStreamId() == channelId)
                {
                    managementIterator.remove();
                }
            }

            final boolean isSuspended = subscriptions.isEmpty();
            if (isSuspended)
            {
                context.suspendController();
            }

            completableFuture.complete(!isSuspended);
        });
        return completableFuture;
    }

    public boolean increaseSubscriptionCreditsAsync(CreditsRequest request)
    {
        actor.call(() ->
        {
            creditsBuffer.handleRequests();
        });

        return this.creditsBuffer.offerRequest(request);
    }

    protected void increaseSubscriptionCredits(CreditsRequest request)
    {
        final long subscriberKey = request.getSubscriberKey();
        final int credits = request.getCredits();

        subscriptions.addCredits(subscriberKey, credits);

        context.resumeController();
    }

    protected TaskSubscription getNextAvailableSubscription()
    {
        TaskSubscription nextSubscription = null;

        if (subscriptions.getTotalCredits() > 0)
        {

            final int subscriptionSize = subscriptions.size();
            int seenSubscriptions = 0;

            while (seenSubscriptions < subscriptionSize && nextSubscription == null)
            {
                if (!taskDistributionIterator.hasNext())
                {
                    taskDistributionIterator.reset();
                }

                final TaskSubscription subscription = taskDistributionIterator.next();
                if (subscription.getCredits() > 0)
                {
                    nextSubscription = subscription;
                }

                seenSubscriptions += 1;
            }
        }
        return nextSubscription;
    }

    public static MetadataFilter eventFilter()
    {
        return m -> m.getEventType() == EventType.TASK_EVENT;
    }

    public static final EventFilter reprocessingEventFilter(final DirectBuffer taskType)
    {
        final TaskEvent taskEvent = new TaskEvent();

        return event ->
        {
            taskEvent.reset();
            event.readValue(taskEvent);

            return BufferUtil.equals(taskEvent.getType(), taskType);
        };
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventKey = event.getKey();

        taskEvent.reset();
        event.readValue(taskEvent);

        EventProcessor eventProcessor = null;

        if (BufferUtil.equals(taskEvent.getType(), subscribedTaskType))
        {
            switch (taskEvent.getState())
            {
                case CREATED:
                case LOCK_EXPIRED:
                case FAILED:
                case RETRIES_UPDATED:
                    eventProcessor = this;
                    break;

                default:
                    break;
            }
        }
        return eventProcessor;
    }

    @Override
    public void processEvent()
    {
        hasLockedTask = false;

        if (taskEvent.getRetries() > 0)
        {
            lockSubscription = getNextAvailableSubscription();
            if (lockSubscription != null)
            {
                final long lockTimeout = ActorClock.currentTimeMillis() + lockSubscription.getLockDuration();

                taskEvent
                    .setState(TaskState.LOCK)
                    .setLockTime(lockTimeout)
                    .setLockOwner(lockSubscription.getLockOwner());

                hasLockedTask = true;
            }
        }
    }

    @Override
    public long writeEvent(LogStreamWriter writer)
    {
        long position = 0;

        if (hasLockedTask)
        {
            targetEventMetadata.reset();

            targetEventMetadata
                .requestStreamId(lockSubscription.getStreamId())
                .subscriberKey(lockSubscription.getSubscriberKey())
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(TASK_EVENT);

            position = writer.key(eventKey)
                    .metadataWriter(targetEventMetadata)
                    .valueWriter(taskEvent)
                    .tryWrite();
        }
        return position;
    }

    @Override
    public void updateState()
    {
        if (hasLockedTask)
        {
            subscriptions.addCredits(lockSubscription.getSubscriberKey(), -1);

            if (subscriptions.getTotalCredits() <= 0)
            {
                context.suspendController();
            }
        }
    }

}
