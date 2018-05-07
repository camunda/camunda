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

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.task.CreditsRequest;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.data.TaskRecord;
import io.zeebe.broker.task.map.TaskInstanceMap;
import io.zeebe.broker.transport.clientapi.SubscribedRecordWriter;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.TaskIntent;
import io.zeebe.util.buffer.BufferUtil;

public class TaskInstanceStreamProcessor
{
    protected static final short STATE_CREATED = 1;
    protected static final short STATE_LOCKED = 2;
    protected static final short STATE_FAILED = 3;
    protected static final short STATE_LOCK_EXPIRED = 4;

    protected SubscribedRecordWriter subscribedEventWriter;
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
        this.subscribedEventWriter = new SubscribedRecordWriter(environment.getOutput());

        return environment.newStreamProcessor()
            .onCommand(ValueType.TASK, TaskIntent.CREATE, new CreateTaskProcessor())
            .onCommand(ValueType.TASK, TaskIntent.LOCK, new LockTaskProcessor())
            .onCommand(ValueType.TASK, TaskIntent.COMPLETE, new CompleteTaskProcessor())
            .onCommand(ValueType.TASK, TaskIntent.FAIL, new FailTaskProcessor())
            .onCommand(ValueType.TASK, TaskIntent.EXPIRE_LOCK, new ExpireLockTaskProcessor())
            .onCommand(ValueType.TASK, TaskIntent.UPDATE_RETRIES, new UpdateRetriesTaskProcessor())
            .onCommand(ValueType.TASK, TaskIntent.CANCEL, new CancelTaskProcessor())
            .withStateResource(taskIndex.getMap())
            .build();
    }

    private class CreateTaskProcessor implements TypedRecordProcessor<TaskRecord>
    {

        @Override
        public boolean executeSideEffects(TypedRecord<TaskRecord> command, TypedResponseWriter responseWriter)
        {
            boolean success = true;

            if (command.getMetadata().hasRequestMetadata())
            {
                success = responseWriter.writeEvent(TaskIntent.CREATED, command);
            }

            return success;
        }

        @Override
        public long writeRecord(TypedRecord<TaskRecord> command, TypedStreamWriter writer)
        {
            return writer.writeFollowUpEvent(command.getKey(), TaskIntent.CREATED, command.getValue());
        }

        @Override
        public void updateState(TypedRecord<TaskRecord> command)
        {
            taskIndex
                .newTaskInstance(command.getKey())
                .setState(STATE_CREATED)
                .write();
        }
    }

    private class LockTaskProcessor implements TypedRecordProcessor<TaskRecord>
    {
        protected boolean isLocked;
        protected final CreditsRequest creditsRequest = new CreditsRequest();

        @Override
        public void processRecord(TypedRecord<TaskRecord> command)
        {
            isLocked = false;

            final short state = taskIndex.wrapTaskInstanceKey(command.getKey()).getState();

            if (state == STATE_CREATED || state == STATE_FAILED || state == STATE_LOCK_EXPIRED)
            {
                isLocked = true;
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<TaskRecord> command, TypedResponseWriter responseWriter)
        {
            boolean success = true;

            if (isLocked)
            {
                final RecordMetadata metadata = command.getMetadata();

                success = subscribedEventWriter
                        .recordType(RecordType.EVENT)
                        .intent(TaskIntent.LOCKED)
                        .partitionId(logStreamPartitionId)
                        .position(command.getPosition())
                        .key(command.getKey())
                        .subscriberKey(metadata.getSubscriberKey())
                        .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
                        .valueType(ValueType.TASK)
                        .valueWriter(command.getValue())
                        .tryWriteMessage(metadata.getRequestStreamId());
            }
            else
            {
                final long subscriptionId = command.getMetadata().getSubscriberKey();

                creditsRequest.setSubscriberKey(subscriptionId);
                creditsRequest.setCredits(1);
                success = taskSubscriptionManager.increaseSubscriptionCreditsAsync(creditsRequest);
            }

            return success;
        }

        @Override
        public long writeRecord(TypedRecord<TaskRecord> command, TypedStreamWriter writer)
        {
            if (isLocked)
            {
                return writer.writeFollowUpEvent(command.getKey(), TaskIntent.LOCKED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<TaskRecord> command)
        {
            if (isLocked)
            {
                taskIndex
                    .setState(STATE_LOCKED)
                    .setLockOwner(command.getValue().getLockOwner())
                    .write();
            }
        }
    }

    private class CompleteTaskProcessor implements TypedRecordProcessor<TaskRecord>
    {
        protected boolean isCompleted;

        @Override
        public void processRecord(TypedRecord<TaskRecord> event)
        {
            isCompleted = false;

            taskIndex.wrapTaskInstanceKey(event.getKey());
            final short state = taskIndex.getState();

            final TaskRecord value = event.getValue();

            final boolean isCompletable = state == STATE_LOCKED || state == STATE_LOCK_EXPIRED;
            if (isCompletable)
            {
                final DirectBuffer payload = value.getPayload();
                if (isNilPayload(payload) || isValidPayload(payload))
                {
                    if (BufferUtil.contentsEqual(taskIndex.getLockOwner(), value.getLockOwner()))
                    {
                        isCompleted = true;
                    }
                }
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<TaskRecord> event, TypedResponseWriter responseWriter)
        {
            if (isCompleted)
            {
                return responseWriter.writeEvent(TaskIntent.COMPLETED, event);
            }
            else
            {
                return responseWriter.writeRejection(event);
            }
        }

        @Override
        public long writeRecord(TypedRecord<TaskRecord> event, TypedStreamWriter writer)
        {
            if (isCompleted)
            {
                return writer.writeFollowUpEvent(event.getKey(), TaskIntent.COMPLETED, event.getValue());
            }
            else
            {
                return writer.writeRejection(event);
            }
        }

        @Override
        public void updateState(TypedRecord<TaskRecord> event)
        {
            if (isCompleted)
            {
                taskIndex.remove(event.getKey());
            }
        }
    }

    private class FailTaskProcessor implements TypedRecordProcessor<TaskRecord>
    {
        protected boolean isFailed;

        @Override
        public void processRecord(TypedRecord<TaskRecord> command)
        {
            isFailed = false;

            final TaskRecord value = command.getValue();

            taskIndex.wrapTaskInstanceKey(command.getKey());
            if (taskIndex.getState() == STATE_LOCKED && BufferUtil.contentsEqual(taskIndex.getLockOwner(), value.getLockOwner()))
            {
                isFailed = true;
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<TaskRecord> command, TypedResponseWriter responseWriter)
        {
            if (isFailed)
            {
                return responseWriter.writeEvent(TaskIntent.FAILED, command);
            }
            else
            {
                return responseWriter.writeRejection(command);
            }
        }

        @Override
        public long writeRecord(TypedRecord<TaskRecord> command, TypedStreamWriter writer)
        {
            if (isFailed)
            {
                return writer.writeFollowUpEvent(command.getKey(), TaskIntent.FAILED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<TaskRecord> command)
        {
            if (isFailed)
            {
                taskIndex
                    .setState(STATE_FAILED)
                    .write();
            }
        }
    }

    private class ExpireLockTaskProcessor implements TypedRecordProcessor<TaskRecord>
    {
        protected boolean isExpired;

        @Override
        public void processRecord(TypedRecord<TaskRecord> command)
        {
            isExpired = false;

            taskIndex.wrapTaskInstanceKey(command.getKey());

            if (taskIndex.getState() == STATE_LOCKED)
            {
                isExpired = true;
            }
        }

        @Override
        public long writeRecord(TypedRecord<TaskRecord> command, TypedStreamWriter writer)
        {
            if (isExpired)
            {
                return writer.writeFollowUpEvent(command.getKey(), TaskIntent.LOCK_EXPIRED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<TaskRecord> command)
        {
            if (isExpired)
            {
                taskIndex
                    .setState(STATE_LOCK_EXPIRED)
                    .write();
            }
        }
    }

    private class UpdateRetriesTaskProcessor implements TypedRecordProcessor<TaskRecord>
    {
        private boolean success;

        @Override
        public void processRecord(TypedRecord<TaskRecord> command)
        {
            final short state = taskIndex.wrapTaskInstanceKey(command.getKey()).getState();
            final TaskRecord value = command.getValue();
            success = state == STATE_FAILED && value.getRetries() > 0;
        }

        @Override
        public boolean executeSideEffects(TypedRecord<TaskRecord> command, TypedResponseWriter responseWriter)
        {
            if (success)
            {
                return responseWriter.writeEvent(TaskIntent.RETRIES_UPDATED, command);
            }
            else
            {
                return responseWriter.writeRejection(command);
            }
        }

        @Override
        public long writeRecord(TypedRecord<TaskRecord> command, TypedStreamWriter writer)
        {
            if (success)
            {
                return writer.writeFollowUpEvent(command.getKey(), TaskIntent.RETRIES_UPDATED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }
    }

    private class CancelTaskProcessor implements TypedRecordProcessor<TaskRecord>
    {
        private boolean isCanceled;

        @Override
        public void processRecord(TypedRecord<TaskRecord> command)
        {
            final short state = taskIndex.wrapTaskInstanceKey(command.getKey()).getState();
            isCanceled = state > 0;
        }

        @Override
        public long writeRecord(TypedRecord<TaskRecord> command, TypedStreamWriter writer)
        {
            if (isCanceled)
            {
                return writer.writeFollowUpEvent(command.getKey(), TaskIntent.CANCELED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<TaskRecord> command)
        {
            if (isCanceled)
            {
                taskIndex.remove(command.getKey());
            }
        }
    }
}
