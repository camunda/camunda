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
package io.zeebe.broker.job;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.job.JobStateController.State;
import io.zeebe.broker.job.old.CreditsRequest;
import io.zeebe.broker.job.old.JobSubscriptionManager;
import io.zeebe.broker.job.old.JobSubscriptionProcessor;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedCommandWriter;
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
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class JobStreamProcessor implements StreamProcessorLifecycleAware {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobSubscriptionManager subscriptionManager;
  private final JobStateController state = new JobStateController();

  private SubscribedRecordWriter subscribedEventWriter;
  private int partitionId;

  private ScheduledTimer timer;
  private TypedCommandWriter writer;
  private JobSubscriptionProcessor jobSubscriptionProcessor;

  public JobStreamProcessor(final JobSubscriptionManager subscriptionManager) {
    this.subscriptionManager = subscriptionManager;
  }

  @Override
  public void onRecovered(TypedStreamProcessor streamProcessor) {
    timer =
        streamProcessor
            .getActor()
            .runAtFixedRate(TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
    writer = streamProcessor.getEnvironment().buildCommandWriter();
    subscriptionManager.addPartition(partitionId, jobSubscriptionProcessor);
  }

  @Override
  public void onClose() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }

    subscriptionManager.removePartition(partitionId);
  }

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment) {
    jobSubscriptionProcessor = new JobSubscriptionProcessor(state);
    this.partitionId = environment.getStream().getPartitionId();
    this.subscribedEventWriter = new SubscribedRecordWriter(environment.getOutput());

    return environment
        .newStreamProcessor()
        .keyGenerator(KeyGenerator.createJobKeyGenerator(this.partitionId, state))
        .onCommand(ValueType.JOB, JobIntent.CREATE, new CreateProcessor())
        .onCommand(ValueType.JOB, JobIntent.ACTIVATE, new ActivateProcessor())
        .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteProcessor())
        .onCommand(ValueType.JOB, JobIntent.FAIL, new FailProcessor())
        .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new TimeOutProcessor())
        .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesProcessor())
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelProcessor())

        // subscription handling
        .onEvent(ValueType.JOB, JobIntent.CREATED, jobSubscriptionProcessor)
        .onEvent(ValueType.JOB, JobIntent.FAILED, jobSubscriptionProcessor)
        .onEvent(ValueType.JOB, JobIntent.TIMED_OUT, jobSubscriptionProcessor)
        .onEvent(ValueType.JOB, JobIntent.RETRIES_UPDATED, jobSubscriptionProcessor)
        .withStateController(state)
        .withListener(this)
        .withListener(jobSubscriptionProcessor)
        .build();
  }

  StateSnapshotController createSnapshotController(StateStorage storage) {
    return new StateSnapshotController(state, storage);
  }

  private void deactivateTimedOutJobs() {
    final Instant now = Instant.ofEpochMilli(ActorClock.currentTimeMillis());
    Loggers.SYSTEM_LOGGER.debug("Checking for jobs with deadline < {}", now.toEpochMilli());
    state.forEachTimedOutEntry(
        now,
        (key, record, control) -> {
          writer.writeFollowUpCommand(
              key, JobIntent.TIME_OUT, record, (m) -> m.valueType(ValueType.JOB));
          writer.flush();
          // we don't have to check for write errors as the job will then be picked up again
          // on the next iteration of this timer
        });
  }

  private class CreateProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      final long key = commandControl.accept(JobIntent.CREATED);
      state.create(key, command);
    }
  }

  private class ActivateProcessor implements TypedRecordProcessor<JobRecord> {
    protected final CreditsRequest creditsRequest = new CreditsRequest();

    private int requestStreamId;

    private SideEffectProducer returnCredits =
        () -> subscriptionManager.increaseSubscriptionCreditsAsync(creditsRequest);

    private SideEffectProducer pushRecord =
        () -> subscribedEventWriter.tryWriteMessage(requestStreamId);

    @Override
    public void processRecord(
        TypedRecord<JobRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter,
        Consumer<SideEffectProducer> sideEffect) {
      if (state.exists(record, State.ACTIVATABLE)) {
        state.activate(record);
        streamWriter.writeFollowUpEvent(record.getKey(), JobIntent.ACTIVATED, record.getValue());
        pushToSubscription(record, sideEffect);
      } else {
        streamWriter.writeRejection(
            record, RejectionType.NOT_APPLICABLE, "Job not currently activatable");
        updateCredits(record, sideEffect);
      }
    }

    private void updateCredits(
        TypedRecord<JobRecord> record, Consumer<SideEffectProducer> sideEffect) {
      creditsRequest.setSubscriberKey(record.getMetadata().getSubscriberKey());
      creditsRequest.setCredits(1);
      sideEffect.accept(returnCredits);
    }

    private void pushToSubscription(
        TypedRecord<JobRecord> record, Consumer<SideEffectProducer> sideEffect) {
      subscribedEventWriter
          .recordType(RecordType.EVENT)
          .intent(JobIntent.ACTIVATED)
          .partitionId(partitionId)
          .position(record.getPosition())
          .sourceRecordPosition(record.getPosition())
          .key(record.getKey())
          .timestamp(record.getTimestamp())
          .subscriberKey(record.getMetadata().getSubscriberKey())
          .subscriptionType(SubscriptionType.JOB_SUBSCRIPTION)
          .valueType(ValueType.JOB)
          .valueWriter(record.getValue());

      requestStreamId = record.getMetadata().getRequestStreamId();

      sideEffect.accept(pushRecord);
    }
  }

  private class CompleteProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      if (state.exists(command)) {
        if (!state.exists(command, State.FAILED)) {
          state.delete(command);
          commandControl.accept(JobIntent.COMPLETED);
        } else {
          commandControl.reject(
              RejectionType.NOT_APPLICABLE, "Job is failed and must be resolved first");
        }
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job does not exist");
      }
    }
  }

  private class FailProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      if (state.exists(command, State.ACTIVATED)) {
        state.fail(command);
        commandControl.accept(JobIntent.FAILED);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not currently activated");
      }
    }
  }

  private class CancelProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      if (state.exists(command)) {
        state.delete(command);
        commandControl.accept(JobIntent.CANCELED);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job does not exist");
      }
    }
  }

  private class TimeOutProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      if (state.exists(command, State.ACTIVATED)) {
        state.timeout(command);
        commandControl.accept(JobIntent.TIMED_OUT);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job not activated");
      }
    }
  }

  private class UpdateRetriesProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(TypedRecord<JobRecord> command, CommandControl commandControl) {
      if (state.exists(command, State.FAILED)) {
        if (command.getValue().getRetries() > 0) {
          state.resolve(command);
          commandControl.accept(JobIntent.RETRIES_UPDATED);
        } else {
          commandControl.reject(RejectionType.BAD_VALUE, "Job retries must be positive");
        }
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not failed");
      }
    }
  }
}
