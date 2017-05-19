/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.task.processor;

import static org.camunda.tngp.protocol.clientapi.EventType.*;
import static org.camunda.tngp.util.EnsureUtil.*;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.NoopSnapshotSupport;
import org.camunda.tngp.broker.task.CreditsRequest;
import org.camunda.tngp.broker.task.CreditsRequestBuffer;
import org.camunda.tngp.broker.task.TaskSubscriptionManager;
import org.camunda.tngp.broker.task.data.TaskEvent;
import org.camunda.tngp.broker.task.data.TaskEventType;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventFilter;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.buffer.BufferUtil;
import org.camunda.tngp.util.time.ClockUtil;

public class LockTaskStreamProcessor implements StreamProcessor, EventProcessor
{
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final NoopSnapshotSupport noopSnapshotSupport = new NoopSnapshotSupport();
    protected DeferredCommandContext cmdQueue;
    protected CreditsRequestBuffer creditsBuffer = new CreditsRequestBuffer(TaskSubscriptionManager.NUM_CONCURRENT_REQUESTS, this::increaseSubscriptionCredits);

    protected final Long2ObjectHashMap<TaskSubscription> subscriptionsById = new Long2ObjectHashMap<>();
    protected Iterator<TaskSubscription> subscriptionIterator;

    protected final DirectBuffer subscriptedTaskType;

    protected DirectBuffer logStreamTopicName;
    protected int logStreamPartitionId;

    protected LogStream targetStream;

    protected int availableSubscriptionCredits = 0;

    protected final TaskEvent taskEvent = new TaskEvent();
    protected long eventKey = 0;

    protected boolean hasLockedTask;
    protected TaskSubscription lockSubscription;

    // activate the processor while adding the first subscription
    protected boolean isSuspended = true;

    public LockTaskStreamProcessor(DirectBuffer taskType)
    {
        this.subscriptedTaskType = taskType;
        this.subscriptionIterator = subscriptionsById.values().iterator();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        // need to restore the log position
        return noopSnapshotSupport;
    }

    @Override
    public boolean isSuspended()
    {
        creditsBuffer.handleRequests();

        return isSuspended;
    }

    public DirectBuffer getSubscriptedTaskType()
    {
        return subscriptedTaskType;
    }

    public DirectBuffer getLogStreamTopicName()
    {
        return logStreamTopicName;
    }

    public int getLogStreamPartitionId()
    {
        return logStreamPartitionId;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        cmdQueue = context.getStreamProcessorCmdQueue();

        final LogStream sourceStream = context.getSourceStream();
        logStreamTopicName = sourceStream.getTopicName();
        logStreamPartitionId = sourceStream.getPartitionId();

        targetStream = context.getTargetStream();
    }

    public CompletableFuture<Void> addSubscription(TaskSubscription subscription)
    {
        ensureNotNull("subscription", subscription);
        ensureNotNull("lock task type", subscription.getLockTaskType());
        ensureGreaterThan("lock duration", subscription.getLockDuration(), 0);
        ensureGreaterThanOrEqual("lock owner", subscription.getLockOwner(), 0);
        ensureGreaterThan("subscription credits", subscription.getCredits(), 0);

        if (!BufferUtil.equals(subscription.getLockTaskType(), subscriptedTaskType))
        {
            final String errorMessage = String.format("Subscription task type is not equal to '%s'.", BufferUtil.bufferAsString(subscriptedTaskType));
            throw new RuntimeException(errorMessage);
        }

        return cmdQueue.runAsync(future ->
        {
            subscriptionsById.put(subscription.getSubscriberKey(), subscription);

            availableSubscriptionCredits += subscription.getCredits();

            isSuspended = false;

            future.complete(null);
        });
    }

    public CompletableFuture<Boolean> removeSubscription(long subscriptionId)
    {
        return cmdQueue.runAsync(future ->
        {
            final TaskSubscription subscription = subscriptionsById.remove(subscriptionId);
            final boolean hasSubscriptions = onRemove(subscription);

            future.complete(hasSubscriptions);
        });
    }

    protected boolean onRemove(TaskSubscription subscription)
    {
        if (subscription != null)
        {
            availableSubscriptionCredits -= subscription.getCredits();
        }

        final boolean hasSubscriptions = subscriptionsById.size() > 0;
        if (!hasSubscriptions)
        {
            isSuspended = true;
        }

        return hasSubscriptions;
    }

    public CompletableFuture<Boolean> onClientChannelCloseAsync(int channelId)
    {
        return cmdQueue.runAsync(future ->
        {
            final Iterator<TaskSubscription> subscriptionIt = subscriptionsById.values().iterator();

            boolean hasSubscriptions = true;
            while (subscriptionIt.hasNext())
            {
                final TaskSubscription subscription = subscriptionIt.next();
                if (subscription.getChannelId() == channelId)
                {
                    subscriptionIt.remove();
                    hasSubscriptions = onRemove(subscription);
                }
            }

            future.complete(hasSubscriptions);
        });
    }

    public boolean increaseSubscriptionCreditsAsync(CreditsRequest request)
    {
        return this.creditsBuffer.offerRequest(request);
    }

    protected void increaseSubscriptionCredits(CreditsRequest request)
    {
        final long subscriberKey = request.getSubscriberKey();
        final int credits = request.getCredits();
        final TaskSubscription subscription = subscriptionsById.get(subscriberKey);

        if (subscription != null)
        {
            availableSubscriptionCredits += credits;
            subscription.setCredits(subscription.getCredits() + credits);

            isSuspended = false;
        }
    }

    protected TaskSubscription getNextAvailableSubscription()
    {
        TaskSubscription nextSubscription = null;

        if (availableSubscriptionCredits > 0)
        {
            final int subscriptionSize = subscriptionsById.size();
            int seenSubscriptions = 0;

            while (seenSubscriptions < subscriptionSize && nextSubscription == null)
            {
                if (!subscriptionIterator.hasNext())
                {
                    // assuming that it just reset the existing iterator internally
                    subscriptionIterator = subscriptionsById.values().iterator();
                }

                final TaskSubscription subscription = subscriptionIterator.next();
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

        if (BufferUtil.equals(taskEvent.getType(), subscriptedTaskType))
        {
            switch (taskEvent.getEventType())
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
                final long lockTimeout = ClockUtil.getCurrentTimeInMillis() + lockSubscription.getLockDuration();

                taskEvent
                    .setEventType(TaskEventType.LOCK)
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
                .reqChannelId(lockSubscription.getChannelId())
                .subscriberKey(lockSubscription.getSubscriberKey())
                .protocolVersion(Constants.PROTOCOL_VERSION)
                .raftTermId(targetStream.getTerm())
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
            final int credits = lockSubscription.getCredits();
            lockSubscription.setCredits(credits - 1);

            availableSubscriptionCredits -= 1;

            if (availableSubscriptionCredits <= 0)
            {
                isSuspended = true;
            }
        }
    }

}
