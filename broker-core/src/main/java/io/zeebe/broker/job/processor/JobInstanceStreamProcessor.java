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
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.transport.clientapi.SubscribedRecordWriter;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.JobIntent;

public class JobInstanceStreamProcessor
{
    protected static final short STATE_CREATED = 1;
    protected static final short STATE_ACTIVATED = 2;
    protected static final short STATE_FAILED = 3;
    protected static final short STATE_TIMED_OUT = 4;

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
            .onCommand(ValueType.JOB, JobIntent.ACTIVATE, new ActivateJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.FAIL, new FailJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new TimeOutJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesJobProcessor())
            .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelJobProcessor())
            .withStateResource(jobIndex.getMap())
            .build();
    }

    private class CreateJobProcessor implements CommandProcessor<JobRecord>
    {

        @Override
        public CommandResult onCommand(TypedRecord<JobRecord> command, CommandControl commandControl)
        {
            return commandControl.accept(JobIntent.CREATED);
        }

        @Override
        public void updateStateOnAccept(TypedRecord<JobRecord> command)
        {
            jobIndex.putJobInstance(command.getKey(), STATE_CREATED);
        }
    }

    private class ActivateJobProcessor implements TypedRecordProcessor<JobRecord>
    {
        protected boolean canActivate;
        protected final CreditsRequest creditsRequest = new CreditsRequest();

        @Override
        public void processRecord(TypedRecord<JobRecord> command)
        {
            canActivate = false;

            final short state = jobIndex.getJobState(command.getKey());

            if (state == STATE_CREATED || state == STATE_FAILED || state == STATE_TIMED_OUT)
            {
                canActivate = true;
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<JobRecord> command, TypedResponseWriter responseWriter)
        {
            boolean success = true;

            if (canActivate)
            {
                final RecordMetadata metadata = command.getMetadata();

                success = subscribedEventWriter
                        .recordType(RecordType.EVENT)
                        .intent(JobIntent.ACTIVATED)
                        .partitionId(logStreamPartitionId)
                        .position(command.getPosition())
                        .key(command.getKey())
                        .timestamp(command.getTimestamp())
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
            if (canActivate)
            {
                return writer.writeFollowUpEvent(command.getKey(), JobIntent.ACTIVATED, command.getValue());
            }
            else
            {
                return writer.writeRejection(command,
                        RejectionType.NOT_APPLICABLE,
                        "Job is not in one of these states: CREATED, FAILED, TIMED_OUT");
            }
        }

        @Override
        public void updateState(TypedRecord<JobRecord> command)
        {
            if (canActivate)
            {
                jobIndex.putJobInstance(command.getKey(), STATE_ACTIVATED);
            }
        }
    }

    private class CompleteJobProcessor implements CommandProcessor<JobRecord>
    {
        @Override
        public CommandResult onCommand(TypedRecord<JobRecord> command, CommandControl commandControl)
        {
            final short state = jobIndex.getJobState(command.getKey());

            final JobRecord value = command.getValue();

            final boolean isCompletable = state == STATE_ACTIVATED || state == STATE_TIMED_OUT;
            if (isCompletable)
            {
                final DirectBuffer payload = value.getPayload();
                if (isNilPayload(payload) || isValidPayload(payload))
                {
                    return commandControl.accept(JobIntent.COMPLETED);
                }
                else
                {
                    return commandControl.reject(RejectionType.BAD_VALUE, "Payload is not a valid msgpack-encoded JSON object or nil");
                }
            }
            else
            {
                return commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not in state: ACTIVATED, TIMED_OUT");
            }
        }

        @Override
        public void updateStateOnAccept(TypedRecord<JobRecord> command)
        {
            jobIndex.remove(command.getKey());
        }
    }

    private class FailJobProcessor implements CommandProcessor<JobRecord>
    {
        @Override
        public CommandResult onCommand(TypedRecord<JobRecord> command, CommandControl commandControl)
        {
            final short state = jobIndex.getJobState(command.getKey());

            if (state == STATE_ACTIVATED)
            {
                return commandControl.accept(JobIntent.FAILED);
            }
            else
            {
                return commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not in state ACTIVATED");
            }
        }

        @Override
        public void updateStateOnAccept(TypedRecord<JobRecord> command)
        {
            jobIndex.putJobInstance(command.getKey(), STATE_FAILED);
        }
    }

    private class TimeOutJobProcessor implements CommandProcessor<JobRecord>
    {
        private static final String REJECTION_REASON = "Job is not in state ACTIVATED";

        @Override
        public CommandResult onCommand(TypedRecord<JobRecord> command, CommandControl commandControl)
        {
            final short state = jobIndex.getJobState(command.getKey());

            if (state == STATE_ACTIVATED)
            {
                return commandControl.accept(JobIntent.TIMED_OUT);
            }
            else
            {
                return commandControl.reject(RejectionType.NOT_APPLICABLE, REJECTION_REASON);
            }
        }

        @Override
        public void updateStateOnAccept(TypedRecord<JobRecord> command)
        {
            jobIndex.putJobInstance(command.getKey(), STATE_TIMED_OUT);
        }
    }

    private class UpdateRetriesJobProcessor implements CommandProcessor<JobRecord>
    {
        @Override
        public CommandResult onCommand(TypedRecord<JobRecord> command, CommandControl commandControl)
        {
            final short state = jobIndex.getJobState(command.getKey());
            final JobRecord value = command.getValue();

            if (state == STATE_FAILED)
            {
                if (value.getRetries() > 0)
                {
                    return commandControl.accept(JobIntent.RETRIES_UPDATED);
                }
                else
                {
                    return commandControl.reject(RejectionType.BAD_VALUE, "Retries must be greater than 0");
                }
            }
            else
            {
                return commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not in state FAILED");
            }
        }
    }

    private class CancelJobProcessor implements CommandProcessor<JobRecord>
    {
        @Override
        public CommandResult onCommand(TypedRecord<JobRecord> command, CommandControl commandControl)
        {

            final short state = jobIndex.getJobState(command.getKey());
            if (state > 0)
            {
                return commandControl.accept(JobIntent.CANCELED);
            }
            else
            {
                return commandControl.reject(RejectionType.NOT_APPLICABLE, "Job does not exist");
            }
        }

        @Override
        public void updateStateOnAccept(TypedRecord<JobRecord> command)
        {
            jobIndex.remove(command.getKey());
        }
    }
}
