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
package org.camunda.tngp.broker.taskqueue.processor;

import static org.camunda.tngp.protocol.clientapi.EventType.*;

import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.processor.BrokerStreamProcessor;
import org.camunda.tngp.broker.logstreams.processor.NoopSnapshotSupport;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorCommand;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.util.EnsureUtil;
import org.camunda.tngp.util.time.ClockUtil;

public class LockTaskStreamProcessor extends BrokerStreamProcessor
{
    protected final LockTaskEventProcessor lockTaskEventProcessor = new LockTaskEventProcessor();

    protected final NoopSnapshotSupport noopSnapshotSupport = new NoopSnapshotSupport();

    protected final TaskEvent taskEvent = new TaskEvent();

    protected final TaskSubscriptions subscriptions = new TaskSubscriptions();

    protected ManyToOneConcurrentArrayQueue<StreamProcessorCommand> cmdQueue;

    protected long eventPosition = 0;
    protected long eventKey = 0;

    protected boolean isSuspended = false;

    public LockTaskStreamProcessor()
    {
        super(TASK_EVENT);
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        // need the snapshot resource to restore the log position
        return noopSnapshotSupport;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        cmdQueue = context.getStreamProcessorCmdQueue();
    }

    @Override
    public boolean isSuspended()
    {
        return isSuspended;
    }

    public CompletableFuture<Void> addSubscription(TaskSubscription subscription)
    {
        EnsureUtil.ensureNotNull("subscription", subscription);
        EnsureUtil.ensureGreaterThan("subscription credits", subscription.getCredits(), 0);

        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            subscriptions.addSubscription(subscription);

            isSuspended = false;

            future.complete(null);
        });

        return future;
    }

    public CompletableFuture<Boolean> removeSubscription(long subscriptionId)
    {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            subscriptions.removeSubscription(subscriptionId);

            final boolean hasSubscriptions = !subscriptions.isEmpty();
            if (!hasSubscriptions)
            {
                isSuspended = true;
            }

            future.complete(hasSubscriptions);
        });

        return future;
    }

    public CompletableFuture<Void> updateSubscriptionCredits(long subscriptionId, int credits)
    {
        EnsureUtil.ensureGreaterThan("subscription credits", credits, 0);

        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            final boolean updated = subscriptions.updateSubscriptionCredits(subscriptionId, credits);
            if (updated)
            {
                isSuspended = false;
                future.complete(null);
            }
            else
            {
                final String errorMessage = String.format("Cannot update the subscription credits. Subscription with id '%s' not found.", subscriptionId);
                future.completeExceptionally(new RuntimeException(errorMessage));
            }
        });

        return future;
    }

    @Override
    public EventProcessor onCheckedEvent(LoggedEvent event)
    {
        eventPosition = event.getPosition();
        eventKey = event.getLongKey();

        taskEvent.reset();
        event.readValue(taskEvent);

        EventProcessor eventProcessor = null;

        switch (taskEvent.getEventType())
        {
            case CREATED:
            case LOCK_EXPIRED:
                eventProcessor = lockTaskEventProcessor;
                break;

            default:
                break;
        }
        return eventProcessor;
    }

    class LockTaskEventProcessor implements EventProcessor
    {
        protected boolean hasLockedTask;
        protected TaskSubscription lockSubscription;

        @Override
        public void processEvent()
        {
            hasLockedTask = false;

            lockSubscription = subscriptions.getNextAvailableSubscription(taskEvent.getType());
            if (lockSubscription != null)
            {
                final long lockTimeout = ClockUtil.getCurrentTimeInMillis() + lockSubscription.getLockTime();

                taskEvent
                    .setEventType(TaskEventType.LOCK)
                    .setLockTime(lockTimeout);

                hasLockedTask = true;
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
                    .subscriptionId(lockSubscription.getId())
                    .protocolVersion(Constants.PROTOCOL_VERSION)
                    .eventType(TASK_EVENT);
                // TODO: targetEventMetadata.raftTermId(raftTermId);

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
                subscriptions.updateSubscriptionCredits(lockSubscription.getId(), credits - 1);

                if (!subscriptions.hasSubscriptionsWithCredits())
                {
                    isSuspended = true;
                }
            }
        }
    }

}
