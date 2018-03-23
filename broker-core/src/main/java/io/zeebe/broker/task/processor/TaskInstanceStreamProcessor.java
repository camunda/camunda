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

import static io.zeebe.broker.util.PayloadUtil.isNilPayload;
import static io.zeebe.broker.util.PayloadUtil.isValidPayload;
import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.task.CreditsRequest;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.task.map.TaskInstanceMap;
import io.zeebe.broker.transport.clientapi.SubscribedEventWriter;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.buffer.BufferUtil;

public class TaskInstanceStreamProcessor
{
    protected static final short STATE_CREATED = 1;
    protected static final short STATE_LOCKED = 2;
    protected static final short STATE_FAILED = 3;
    protected static final short STATE_LOCK_EXPIRED = 4;

    protected SubscribedEventWriter subscribedEventWriter;
    protected final TaskSubscriptionManager taskSubscriptionManager;
    protected final CreditsRequest creditsRequest = new CreditsRequest();

    protected final TaskInstanceMap taskIndex;
    protected int logStreamPartitionId;

    public TaskInstanceStreamProcessor(TaskSubscriptionManager taskSubscriptionManager)
    {
        this.taskSubscriptionManager = taskSubscriptionManager;

        this.taskIndex = new TaskInstanceMap();
    }

    public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment)
    {
        this.logStreamPartitionId = environment.getStream().getPartitionId();
        this.subscribedEventWriter = new SubscribedEventWriter(environment.getOutput());

        return environment.newStreamProcessor()
            .onEvent(EventType.TASK_EVENT, TaskState.CREATE, new CreateTaskProcessor())
            .onEvent(EventType.TASK_EVENT, TaskState.LOCK, new LockTaskProcessor())
            .onEvent(EventType.TASK_EVENT, TaskState.COMPLETE, new CompleteTaskProcessor())
            .onEvent(EventType.TASK_EVENT, TaskState.FAIL, new FailTaskProcessor())
            .onEvent(EventType.TASK_EVENT, TaskState.EXPIRE_LOCK, new ExpireLockTaskProcessor())
            .onEvent(EventType.TASK_EVENT, TaskState.UPDATE_RETRIES, new UpdateRetriesTaskProcessor())
            .onEvent(EventType.TASK_EVENT, TaskState.CANCEL, new CancelTaskProcessor())
            .withStateResource(taskIndex.getMap())
            .build();
    }

    private class CreateTaskProcessor implements TypedEventProcessor<TaskEvent>
    {

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            event.getValue().setState(TaskState.CREATED);
        }

        @Override
        public boolean executeSideEffects(TypedEvent<TaskEvent> event, TypedResponseWriter responseWriter)
        {
            boolean success = true;

            if (event.getMetadata().hasRequestMetadata())
            {
                success = responseWriter.write(event);
            }

            return success;
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            taskIndex
                .newTaskInstance(event.getKey())
                .setState(STATE_CREATED)
                .write();
        }
    }

    private class LockTaskProcessor implements TypedEventProcessor<TaskEvent>
    {
        protected boolean isLocked;
        protected final CreditsRequest creditsRequest = new CreditsRequest();

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            isLocked = false;

            final short state = taskIndex.wrapTaskInstanceKey(event.getKey()).getState();

            if (state == STATE_CREATED || state == STATE_FAILED || state == STATE_LOCK_EXPIRED)
            {
                event.getValue().setState(TaskState.LOCKED);
                isLocked = true;
            }
            else
            {
                event.getValue().setState(TaskState.LOCK_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects(TypedEvent<TaskEvent> event, TypedResponseWriter responseWriter)
        {
            boolean success = true;

            if (isLocked)
            {
                final BrokerEventMetadata metadata = event.getMetadata();

                success = subscribedEventWriter
                        .partitionId(logStreamPartitionId)
                        .position(event.getPosition())
                        .key(event.getKey())
                        .subscriberKey(metadata.getSubscriberKey())
                        .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
                        .eventType(TASK_EVENT)
                        .eventWriter(event.getValue())
                        .tryWriteMessage(metadata.getRequestStreamId());
            }
            else
            {
                final long subscriptionId = event.getMetadata().getSubscriberKey();

                creditsRequest.setSubscriberKey(subscriptionId);
                creditsRequest.setCredits(1);
                success = taskSubscriptionManager.increaseSubscriptionCreditsAsync(creditsRequest);
            }

            return success;
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            if (isLocked)
            {
                taskIndex
                    .setState(STATE_LOCKED)
                    .setLockOwner(event.getValue().getLockOwner())
                    .write();
            }
        }
    }

    private class CompleteTaskProcessor implements TypedEventProcessor<TaskEvent>
    {
        protected boolean isCompleted;

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            isCompleted = false;

            taskIndex.wrapTaskInstanceKey(event.getKey());
            final short state = taskIndex.getState();

            TaskState taskEventType = TaskState.COMPLETE_REJECTED;

            final TaskEvent value = event.getValue();

            final boolean isCompletable = state == STATE_LOCKED || state == STATE_LOCK_EXPIRED;
            if (isCompletable)
            {
                final DirectBuffer payload = value.getPayload();
                if (isNilPayload(payload) || isValidPayload(payload))
                {
                    if (BufferUtil.contentsEqual(taskIndex.getLockOwner(), value.getLockOwner()))
                    {
                        taskEventType = TaskState.COMPLETED;
                        isCompleted = true;
                    }
                }
            }

            value.setState(taskEventType);
        }

        @Override
        public boolean executeSideEffects(TypedEvent<TaskEvent> event, TypedResponseWriter responseWriter)
        {
            return responseWriter.write(event);
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            if (isCompleted)
            {
                taskIndex.remove(event.getKey());
            }
        }
    }

    private class FailTaskProcessor implements TypedEventProcessor<TaskEvent>
    {
        protected boolean isFailed;

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            isFailed = false;

            final TaskEvent value = event.getValue();

            taskIndex.wrapTaskInstanceKey(event.getKey());
            if (taskIndex.getState() == STATE_LOCKED && BufferUtil.contentsEqual(taskIndex.getLockOwner(), value.getLockOwner()))
            {
                value.setState(TaskState.FAILED);
                isFailed = true;
            }

            if (!isFailed)
            {
                value.setState(TaskState.FAIL_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects(TypedEvent<TaskEvent> event, TypedResponseWriter responseWriter)
        {
            return responseWriter.write(event);
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            if (isFailed)
            {
                taskIndex
                    .setState(STATE_FAILED)
                    .write();
            }
        }
    }

    private class ExpireLockTaskProcessor implements TypedEventProcessor<TaskEvent>
    {
        protected boolean isExpired;

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            isExpired = false;

            taskIndex.wrapTaskInstanceKey(event.getKey());
            final TaskEvent value = event.getValue();

            if (taskIndex.getState() == STATE_LOCKED)
            {
                value.setState(TaskState.LOCK_EXPIRED);
                isExpired = true;
            }

            if (!isExpired)
            {
                value.setState(TaskState.LOCK_EXPIRATION_REJECTED);
            }
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            if (isExpired)
            {
                taskIndex
                    .setState(STATE_LOCK_EXPIRED)
                    .write();
            }
        }
    }

    private class UpdateRetriesTaskProcessor implements TypedEventProcessor<TaskEvent>
    {
        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            final short state = taskIndex.wrapTaskInstanceKey(event.getKey()).getState();
            final TaskEvent value = event.getValue();

            if (state == STATE_FAILED && value.getRetries() > 0)
            {
                value.setState(TaskState.RETRIES_UPDATED);
            }
            else
            {
                value.setState(TaskState.UPDATE_RETRIES_REJECTED);
            }
        }

        @Override
        public boolean executeSideEffects(TypedEvent<TaskEvent> event, TypedResponseWriter responseWriter)
        {
            return responseWriter.write(event);
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }
    }

    private class CancelTaskProcessor implements TypedEventProcessor<TaskEvent>
    {
        private boolean isCanceled;

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            isCanceled = false;

            final short state = taskIndex.wrapTaskInstanceKey(event.getKey()).getState();
            final TaskEvent value = event.getValue();

            if (state > 0)
            {
                value.setState(TaskState.CANCELED);
                isCanceled = true;
            }
            else
            {
                value.setState(TaskState.CANCEL_REJECTED);
            }
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            if (isCanceled)
            {
                taskIndex.remove(event.getKey());
            }
        }
    }
}
