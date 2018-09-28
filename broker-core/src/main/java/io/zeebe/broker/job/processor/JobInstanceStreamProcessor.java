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

import io.zeebe.broker.job.CreditsRequest;
import io.zeebe.broker.job.JobSubscriptionManager;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.job.state.JobInstanceStateController;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.transport.clientapi.SubscribedRecordWriter;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.JobIntent;
import java.util.function.Consumer;

public class JobInstanceStreamProcessor implements StreamProcessorLifecycleAware {

  protected static final short STATE_CREATED = 1;
  protected static final short STATE_ACTIVATED = 2;
  protected static final short STATE_FAILED = 3;
  protected static final short STATE_TIMED_OUT = 4;

  protected SubscribedRecordWriter subscribedEventWriter;
  protected final JobSubscriptionManager jobSubscriptionManager;
  protected final JobInstanceStateController stateController = new JobInstanceStateController();

  protected int logStreamPartitionId;

  public JobInstanceStreamProcessor(JobSubscriptionManager jobSubscriptionManager) {
    this.jobSubscriptionManager = jobSubscriptionManager;
  }

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment) {
    this.logStreamPartitionId = environment.getStream().getPartitionId();
    this.subscribedEventWriter = new SubscribedRecordWriter(environment.getOutput());

    return environment
        .newStreamProcessor()
        .keyGenerator(KeyGenerator.createJobKeyGenerator(logStreamPartitionId, stateController))
        .onCommand(ValueType.JOB, JobIntent.CREATE, new CreateJobProcessor())
        .onCommand(ValueType.JOB, JobIntent.ACTIVATE, new ActivateJobProcessor())
        .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteJobProcessor())
        .onCommand(ValueType.JOB, JobIntent.FAIL, new FailJobProcessor())
        .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new TimeOutJobProcessor())
        .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesJobProcessor())
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelJobProcessor())
        .withStateController(stateController)
        .withListener(this)
        .build();
  }

  public StateSnapshotController createSnapshotController(StateStorage storage) {
    return new StateSnapshotController(stateController, storage);
  }

  private class CreateJobProcessor implements CommandProcessor<JobRecord> {

    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      final long jobKey = commandControl.accept(JobIntent.CREATED);
      stateController.putJobState(jobKey, STATE_CREATED);
    }
  }

  private class ActivateJobProcessor implements TypedRecordProcessor<JobRecord> {
    protected final CreditsRequest creditsRequest = new CreditsRequest();

    private int requestStreamId;

    private final SideEffectProducer returnCredits =
        () -> jobSubscriptionManager.increaseSubscriptionCreditsAsync(creditsRequest);

    private final SideEffectProducer pushRecord =
        () -> subscribedEventWriter.tryWriteMessage(requestStreamId);

    @Override
    public void processRecord(
        TypedRecord<JobRecord> command,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter,
        Consumer<SideEffectProducer> sideEffect) {

      final short state = stateController.getJobState(command.getKey());

      if (state == STATE_CREATED || state == STATE_FAILED || state == STATE_TIMED_OUT) {
        streamWriter.writeFollowUpEvent(command.getKey(), JobIntent.ACTIVATED, command.getValue());
        stateController.putJobState(command.getKey(), STATE_ACTIVATED);

        final RecordMetadata metadata = command.getMetadata();

        subscribedEventWriter
            .recordType(RecordType.EVENT)
            .intent(JobIntent.ACTIVATED)
            .partitionId(logStreamPartitionId)
            .position(command.getPosition())
            .sourceRecordPosition(command.getPosition())
            .key(command.getKey())
            .timestamp(command.getTimestamp())
            .subscriberKey(metadata.getSubscriberKey())
            .subscriptionType(SubscriptionType.JOB_SUBSCRIPTION)
            .valueType(ValueType.JOB)
            .valueWriter(command.getValue());

        requestStreamId = metadata.getRequestStreamId();

        sideEffect.accept(pushRecord);
      } else {
        streamWriter.writeRejection(
            command,
            RejectionType.NOT_APPLICABLE,
            "Job is not in one of these states: CREATED, FAILED, TIMED_OUT");

        final long subscriptionId = command.getMetadata().getSubscriberKey();

        creditsRequest.setSubscriberKey(subscriptionId);
        creditsRequest.setCredits(1);
        sideEffect.accept(returnCredits);
      }
    }
  }

  private class CompleteJobProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      final short state = stateController.getJobState(command.getKey());

      final boolean isCompletable = state == STATE_ACTIVATED || state == STATE_TIMED_OUT;
      if (isCompletable) {
        stateController.deleteJobState(command.getKey());
        commandControl.accept(JobIntent.COMPLETED);
      } else {
        commandControl.reject(
            RejectionType.NOT_APPLICABLE, "Job is not in state: ACTIVATED, TIMED_OUT");
      }
    }
  }

  private class FailJobProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      final short state = stateController.getJobState(command.getKey());

      if (state == STATE_ACTIVATED) {
        stateController.putJobState(command.getKey(), STATE_FAILED);
        commandControl.accept(JobIntent.FAILED);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not in state ACTIVATED");
      }
    }
  }

  private class TimeOutJobProcessor implements CommandProcessor<JobRecord> {
    private static final String REJECTION_REASON = "Job is not in state ACTIVATED";

    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      final short state = stateController.getJobState(command.getKey());

      if (state == STATE_ACTIVATED) {
        stateController.putJobState(command.getKey(), STATE_TIMED_OUT);
        commandControl.accept(JobIntent.TIMED_OUT);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, REJECTION_REASON);
      }
    }
  }

  private class UpdateRetriesJobProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      final short state = stateController.getJobState(command.getKey());
      final JobRecord value = command.getValue();

      if (state == STATE_FAILED) {
        if (value.getRetries() > 0) {
          commandControl.accept(JobIntent.RETRIES_UPDATED);
        } else {
          commandControl.reject(RejectionType.BAD_VALUE, "Retries must be greater than 0");
        }
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not in state FAILED");
      }
    }
  }

  private class CancelJobProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {

      final short state = stateController.getJobState(command.getKey());
      if (state > 0) {
        stateController.deleteJobState(command.getKey());
        commandControl.accept(JobIntent.CANCELED);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job does not exist");
      }
    }
  }
}
