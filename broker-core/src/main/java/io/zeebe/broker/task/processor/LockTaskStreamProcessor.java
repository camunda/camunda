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

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.zeebe.util.EnsureUtil.ensureLessThanOrEqual;
import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.task.CreditsRequest;
import io.zeebe.broker.task.CreditsRequestBuffer;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.data.TaskRecord;
import io.zeebe.broker.task.processor.TaskSubscriptions.SubscriptionIterator;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class LockTaskStreamProcessor implements TypedRecordProcessor<TaskRecord>, StreamProcessorLifecycleAware
{
    protected final CreditsRequestBuffer creditsBuffer = new CreditsRequestBuffer(TaskSubscriptionManager.NUM_CONCURRENT_REQUESTS, this::increaseSubscriptionCredits);

    private final TaskSubscriptions subscriptions = new TaskSubscriptions(8);
    private final SubscriptionIterator taskDistributionIterator;
    private final SubscriptionIterator managementIterator;

    private final DirectBuffer subscribedTaskType;
    private int partitionId;
    private ActorControl actor;
    private StreamProcessorContext context;

    private TaskSubscription selectedSubscriber;

    public LockTaskStreamProcessor(DirectBuffer taskType)
    {
        this.subscribedTaskType = taskType;
        this.taskDistributionIterator = subscriptions.iterator();
        this.managementIterator = subscriptions.iterator();
    }

    public DirectBuffer getSubscriptedTaskType()
    {
        return subscribedTaskType;
    }

    public int getLogStreamPartitionId()
    {
        return partitionId;
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        this.context = streamProcessor.getStreamProcessorContext();
        this.actor = context.getActorControl();

        // activate the processor while adding the first subscription
        context.suspendController();
    }

    public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment env)
    {
        this.partitionId = env.getStream().getPartitionId();

        return env.newStreamProcessor()
                .onEvent(ValueType.TASK, Intent.CREATED, this)
                .onEvent(ValueType.TASK, Intent.LOCK_EXPIRED, this)
                .onEvent(ValueType.TASK, Intent.FAILED, this)
                .onEvent(ValueType.TASK, Intent.RETRIES_UPDATED, this)
                .build();
    }

    public ActorFuture<Void> addSubscription(TaskSubscription subscription)
    {
        try
        {
            ensureNotNull("subscription", subscription);
            ensureNotNullOrEmpty("lock task type", subscription.getLockTaskType());
            ensureNotNullOrEmpty("lock owner", subscription.getLockOwner());
            ensureGreaterThan("length of lock owner", subscription.getLockOwner().capacity(), 0);
            ensureLessThanOrEqual("length of lock owner", subscription.getLockOwner().capacity(), TaskSubscription.LOCK_OWNER_MAX_LENGTH);
            ensureGreaterThan("lock duration", subscription.getLockDuration(), 0);
            ensureGreaterThan("subscription credits", subscription.getCredits(), 0);
        }
        catch (Exception e)
        {
            return CompletableActorFuture.completedExceptionally(e);
        }

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
        return actor.call(() ->
        {
            subscriptions.removeSubscription(subscriberKey);
            final boolean isSuspended = subscriptions.isEmpty();
            if (isSuspended)
            {
                context.suspendController();
            }

            return !isSuspended;
        });
    }

    public ActorFuture<Boolean> onClientChannelCloseAsync(int channelId)
    {
        return actor.call(() ->
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
            return !isSuspended;
        });
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

    @Override
    public void processRecord(TypedRecord<TaskRecord> event)
    {
        selectedSubscriber = null;

        final TaskRecord taskEvent = event.getValue();
        final boolean handlesTaskType = BufferUtil.equals(taskEvent.getType(), subscribedTaskType);

        if (handlesTaskType && taskEvent.getRetries() > 0)
        {
            selectedSubscriber = getNextAvailableSubscription();
            if (selectedSubscriber != null)
            {
                final long lockTimeout = ActorClock.currentTimeMillis() + selectedSubscriber.getLockDuration();

                taskEvent
                    .setLockTime(lockTimeout)
                    .setLockOwner(selectedSubscriber.getLockOwner());
            }
        }
    }

    @Override
    public long writeRecord(TypedRecord<TaskRecord> event, TypedStreamWriter writer)
    {
        long position = 0;

        if (selectedSubscriber != null)
        {
            position = writer.writeFollowUpCommand(
                event.getKey(),
                Intent.LOCK,
                event.getValue(),
                this::assignToSelectedSubscriber);
        }
        return position;
    }

    private void assignToSelectedSubscriber(RecordMetadata metadata)
    {
        metadata.subscriberKey(selectedSubscriber.getSubscriberKey());
        metadata.requestStreamId(selectedSubscriber.getStreamId());
    }

    @Override
    public void updateState(TypedRecord<TaskRecord> event)
    {
        if (selectedSubscriber != null)
        {
            subscriptions.addCredits(selectedSubscriber.getSubscriberKey(), -1);

            if (subscriptions.getTotalCredits() <= 0)
            {
                context.suspendController();
            }
        }
    }
}
