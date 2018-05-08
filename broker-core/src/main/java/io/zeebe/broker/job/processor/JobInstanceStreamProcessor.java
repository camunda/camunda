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
package io.zeebe.broker.job.processor;

import static io.zeebe.broker.util.PayloadUtil.isNilPayload;
import static io.zeebe.broker.util.PayloadUtil.isValidPayload;

import org.agrona.DirectBuffer;

import io.zeebe.broker.job.CreditsRequest;
import io.zeebe.broker.job.JobSubscriptionManager;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.job.map.JobInstanceMap;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.transport.clientapi.SubscribedRecordWriter;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.buffer.BufferUtil;

public class JobInstanceStreamProcessor
{
    protected static final short STATE_CREATED = 1;
    protected static final short STATE_LOCKED = 2;
    protected static final short STATE_FAILED = 3;
    protected static final short STATE_LOCK_EXPIRED = 4;

    protected SubscribedRecordWriter subscribedEventWriter;
    protected final JobSubscriptionManager jobSubscriptionManager;
    protected final CreditsRequest creditsRequest = new CreditsRequest();

    protected final JobInstanceMap jobIndex;
    protected int logStreamPartitionId;

    public JobInstanceStreamProcessor(JobSubscriptionManager jobSubscriptionManager)
    {
        this.jobSubscriptionManager = jobSubscriptionManager;

        this.jobIndex = new JobInstanceMap();
    }

    public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment)
    {
        this.logStreamPartitionId = environment.getStream().getPartitionId();
        this.subscribedEventWriter = new SubscribedRecordWriter(environment.getOutput());

        return environment.newStreamProcessor()
            .onCommand(ValueType.JOB, JobIntent.CREATE, new CreateJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.LOCK, new LockJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.FAIL, new FailJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.EXPIRE_LOCK, new ExpireLockJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelJobProcessor())
            .withStateResource(jobIndex.getMap())
            .build();
    }

    private class CreateJobProcessor implements TypedRecordProcessor<JobRecord>
    {

        @Override
        public boolean executeSideEffects(TypedRecord<JobRecord> command, TypedResponseWriter responseWriter)
        {
            boolean success = true;

            if (command.getMetadata().hasRequestMetadata())
            {
                success = responseWriter.writeEvent(JobIntent.CREATED, command);
            }

            return success;
        }

        public long writeRecord(TypedRecord<JobRecord> command, TypedStreamWriter writer)
        {
            return writer.writeFollowUpEvent(command.getKey(), JobIntent.CREATED, command.getValue());
        }

        public void updateState(TypedRecord<JobRecord> command)
        {
            jobIndex
                .newJobInstance(command.getKey())
                .setState(STATE_CREATED)
                .write();
        }
    }

    private class LockJobProcessor implements TypedRecordProcessor<JobRecord>
    {
        protected boolean isLocked;
        protected final CreditsRequest creditsRequest = new CreditsRequest();

        @Override
        public void processRecord(TypedRecord<JobRecord> command)
        {
            isLocked = false;

            final short state = jobIndex.wrapJobInstanceKey(command.getKey()).getState();

            if (state == STATE_CREATED || state == STATE_FAILED || state == STATE_LOCK_EXPIRED)
            {
                isLocked = true;
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<JobRecord> command, TypedResponseWriter responseWriter)
        {
            boolean success = true;

            if (isLocked)
            {
                final RecordMetadata metadata = command.getMetadata();

                success = subscribedEventWriter
                        .recordType(RecordType.EVENT)
                        .intent(JobIntent.LOCKED)
                        .partitionId(logStreamPartitionId)
                        .position(command.getPosition())
                        .key(command.getKey())
                        .subscriberKey(metadata.getSubscriberKey())
                        .subscriptionType(SubscriptionType.JOB_SUBSCRIPTION)
                        .valueType(ValueType.JOB)
                        .valueWriter(command.getValue())
                        .tryWriteMessage(metadata.getRequestStreamId());
            }
            else
            {
                final long subscriptionId = command.getMetadata().getSubscriberKey();

                creditsRequest.setSubscriberKey(subscriptionId);
                creditsRequest.setCredits(1);
                success = jobSubscriptionManager.increaseSubscriptionCreditsAsync(creditsRequest);
            }

            return success;
        }

        @Override
        public long writeRecord(TypedRecord<JobRecord> command, TypedStreamWriter writer)
        {
            if (isLocked)
            {
                return writer.writeFollowUpEvent(command.getKey(), JobIntent.LOCKED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<JobRecord> command)
        {
            if (isLocked)
            {
                jobIndex
                    .setState(STATE_LOCKED)
                    .setLockOwner(command.getValue().getLockOwner())
                    .write();
            }
        }
    }

    private class CompleteJobProcessor implements TypedRecordProcessor<JobRecord>
    {
        protected boolean isCompleted;

        @Override
        public void processRecord(TypedRecord<JobRecord> event)
        {
            isCompleted = false;

            jobIndex.wrapJobInstanceKey(event.getKey());
            final short state = jobIndex.getState();

            final JobRecord value = event.getValue();

            final boolean isCompletable = state == STATE_LOCKED || state == STATE_LOCK_EXPIRED;
            if (isCompletable)
            {
                final DirectBuffer payload = value.getPayload();
                if (isNilPayload(payload) || isValidPayload(payload))
                {
                    if (BufferUtil.contentsEqual(jobIndex.getLockOwner(), value.getLockOwner()))
                    {
                        isCompleted = true;
                    }
                }
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<JobRecord> event, TypedResponseWriter responseWriter)
        {
            if (isCompleted)
            {
                return responseWriter.writeEvent(JobIntent.COMPLETED, event);
            }
            else
            {
                return responseWriter.writeRejection(event);
            }
        }

        @Override
        public long writeRecord(TypedRecord<JobRecord> event, TypedStreamWriter writer)
        {
            if (isCompleted)
            {
                return writer.writeFollowUpEvent(event.getKey(), JobIntent.COMPLETED, event.getValue());
            }
            else
            {
                return writer.writeRejection(event);
            }
        }

        @Override
        public void updateState(TypedRecord<JobRecord> event)
        {
            if (isCompleted)
            {
                jobIndex.remove(event.getKey());
            }
        }
    }

    private class FailJobProcessor implements TypedRecordProcessor<JobRecord>
    {
        protected boolean isFailed;

        public void processRecord(TypedRecord<JobRecord> command)
        {
            isFailed = false;

            final JobRecord value = command.getValue();

            jobIndex.wrapJobInstanceKey(command.getKey());
            if (jobIndex.getState() == STATE_LOCKED && BufferUtil.contentsEqual(jobIndex.getLockOwner(), value.getLockOwner()))
            {
                isFailed = true;
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<JobRecord> command, TypedResponseWriter responseWriter)
        {
            if (isFailed)
            {
                return responseWriter.writeEvent(JobIntent.FAILED, command);
            }
            else
            {
                return responseWriter.writeRejection(command);
            }
        }

        @Override
        public long writeRecord(TypedRecord<JobRecord> command, TypedStreamWriter writer)
        {
            if (isFailed)
            {
                return writer.writeFollowUpEvent(command.getKey(), JobIntent.FAILED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<JobRecord> command)
        {
            if (isFailed)
            {
                jobIndex
                    .setState(STATE_FAILED)
                    .write();
            }
        }
    }

    private class ExpireLockJobProcessor implements TypedRecordProcessor<JobRecord>
    {
        protected boolean isExpired;

        @Override
        public void processRecord(TypedRecord<JobRecord> command)
        {
            isExpired = false;

            jobIndex.wrapJobInstanceKey(command.getKey());

            if (jobIndex.getState() == STATE_LOCKED)
            {
                isExpired = true;
            }
        }

        @Override
        public long writeRecord(TypedRecord<JobRecord> command, TypedStreamWriter writer)
        {
            if (isExpired)
            {
                return writer.writeFollowUpEvent(command.getKey(), JobIntent.LOCK_EXPIRED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<JobRecord> command)
        {
            if (isExpired)
            {
                jobIndex
                    .setState(STATE_LOCK_EXPIRED)
                    .write();
            }
        }
    }

    private class UpdateRetriesJobProcessor implements TypedRecordProcessor<JobRecord>
    {
        private boolean success;

        @Override
        public void processRecord(TypedRecord<JobRecord> command)
        {
            final short state = jobIndex.wrapJobInstanceKey(command.getKey()).getState();
            final JobRecord value = command.getValue();
            success = state == STATE_FAILED && value.getRetries() > 0;
        }

        @Override
        public boolean executeSideEffects(TypedRecord<JobRecord> command, TypedResponseWriter responseWriter)
        {
            if (success)
            {
                return responseWriter.writeEvent(JobIntent.RETRIES_UPDATED, command);
            }
            else
            {
                return responseWriter.writeRejection(command);
            }
        }

        @Override
        public long writeRecord(TypedRecord<JobRecord> command, TypedStreamWriter writer)
        {
            if (success)
            {
                return writer.writeFollowUpEvent(command.getKey(), JobIntent.RETRIES_UPDATED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }
    }

    private class CancelJobProcessor implements TypedRecordProcessor<JobRecord>
    {
        private boolean isCanceled;

        @Override
        public void processRecord(TypedRecord<JobRecord> command)
        {
            final short state = jobIndex.wrapJobInstanceKey(command.getKey()).getState();
            isCanceled = state > 0;
        }

        @Override
        public long writeRecord(TypedRecord<JobRecord> command, TypedStreamWriter writer)
        {
            if (isCanceled)
            {
                return writer.writeFollowUpEvent(command.getKey(), JobIntent.CANCELED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command);
            }
        }

        @Override
        public void updateState(TypedRecord<JobRecord> command)
        {
            if (isCanceled)
            {
                jobIndex.remove(command.getKey());
            }
        }
    }
}
