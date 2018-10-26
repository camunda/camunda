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

import static io.zeebe.util.sched.clock.ActorClock.currentTimeMillis;

import io.zeebe.broker.job.JobStateController.State;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
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
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.agrona.ExpandableArrayBuffer;

public class JobStreamProcessor implements StreamProcessorLifecycleAware {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobStateController state = new JobStateController();

  private SubscribedRecordWriter subscribedEventWriter;
  private int partitionId;

  private ScheduledTimer timer;
  private TypedCommandWriter writer;
  private KeyGenerator jobKeyGenerator;

  @Override
  public void onRecovered(TypedStreamProcessor streamProcessor) {
    timer =
        streamProcessor
            .getActor()
            .runAtFixedRate(TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
    writer = streamProcessor.getEnvironment().buildCommandWriter();
  }

  @Override
  public void onClose() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment) {
    this.partitionId = environment.getStream().getPartitionId();
    this.subscribedEventWriter = new SubscribedRecordWriter(environment.getOutput());
    jobKeyGenerator = KeyGenerator.createJobKeyGenerator(this.partitionId, state);

    return environment
        .newStreamProcessor()
        .keyGenerator(jobKeyGenerator)
        .onCommand(ValueType.JOB, JobIntent.CREATE, new CreateProcessor())
        .onCommand(ValueType.JOB, JobIntent.ACTIVATE, new ActivateProcessor())
        .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteProcessor())
        .onCommand(ValueType.JOB, JobIntent.FAIL, new FailProcessor())
        .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new TimeOutProcessor())
        .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesProcessor())
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelProcessor())
        .onCommand(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE, new JobBatchActivateProcessor())
        .withStateController(state)
        .withListener(this)
        .build();
  }

  StateSnapshotController createSnapshotController(StateStorage storage) {
    return new StateSnapshotController(state, storage);
  }

  private void deactivateTimedOutJobs() {
    final long now = currentTimeMillis();
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
    public void onCommand(
        TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
      final long key = commandControl.accept(JobIntent.CREATED, command.getValue());
      state.create(key, command.getValue());
    }
  }

  private class ActivateProcessor implements TypedRecordProcessor<JobRecord> {
    private int requestStreamId;

    private final SideEffectProducer pushRecord =
        () -> subscribedEventWriter.tryWriteMessage(requestStreamId);

    @Override
    public void processRecord(
        TypedRecord<JobRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter,
        Consumer<SideEffectProducer> sideEffect) {
      if (state.isInState(record.getKey(), State.ACTIVATABLE)) {
        state.activate(record.getKey(), record.getValue());
        streamWriter.writeFollowUpEvent(record.getKey(), JobIntent.ACTIVATED, record.getValue());
        pushToSubscription(record, sideEffect);
      } else {
        streamWriter.writeRejection(
            record, RejectionType.NOT_APPLICABLE, "Job not currently activatable");
      }
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
          .subscriptionType(SubscriptionType.JOB_SUBSCRIPTION)
          .valueType(ValueType.JOB)
          .valueWriter(record.getValue());

      requestStreamId = record.getMetadata().getRequestStreamId();

      sideEffect.accept(pushRecord);
    }
  }

  private class CompleteProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(
        TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {

      final long jobKey = command.getKey();

      if (state.exists(jobKey)) {
        if (!state.isInState(jobKey, State.FAILED)) {
          final JobRecord job = state.getJob(jobKey);
          job.setPayload(command.getValue().getPayload());

          state.delete(jobKey, job);
          commandControl.accept(JobIntent.COMPLETED, job);
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
    public void onCommand(
        TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {

      final long key = command.getKey();

      if (state.isInState(key, State.ACTIVATED)) {
        final JobRecord failedJob = state.getJob(key);
        failedJob.setRetries(command.getValue().getRetries());
        state.fail(key, failedJob);
        commandControl.accept(JobIntent.FAILED, failedJob);
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not currently activated");
      }
    }
  }

  private class CancelProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(
        TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
      final long jobKey = command.getKey();

      if (state.exists(jobKey)) {
        state.delete(jobKey, command.getValue());
        commandControl.accept(JobIntent.CANCELED, command.getValue());
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job does not exist");
      }
    }
  }

  private class TimeOutProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(
        TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
      if (state.isInState(command.getKey(), State.ACTIVATED)) {
        state.timeout(command.getKey(), command.getValue());
        commandControl.accept(JobIntent.TIMED_OUT, command.getValue());
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job not activated");
      }
    }
  }

  private class UpdateRetriesProcessor implements CommandProcessor<JobRecord> {
    @Override
    public void onCommand(
        TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
      final long key = command.getKey();
      final int retries = command.getValue().getRetries();

      if (state.isInState(command.getKey(), State.FAILED)) {
        if (retries > 0) {
          final JobRecord failedJob = state.getJob(command.getKey());
          failedJob.setRetries(retries);
          state.resolve(key, failedJob);
          commandControl.accept(JobIntent.RETRIES_UPDATED, failedJob);
        } else {
          commandControl.reject(RejectionType.BAD_VALUE, "Job retries must be positive");
        }
      } else {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not failed");
      }
    }
  }

  private class JobBatchActivateProcessor implements TypedRecordProcessor<JobBatchRecord> {

    @Override
    public void processRecord(
        TypedRecord<JobBatchRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {
      final JobBatchRecord value = record.getValue();
      if (isValid(value)) {
        activateJobs(record, responseWriter, streamWriter);
      } else {
        rejectCommand(record, responseWriter, streamWriter);
      }
    }
  }

  private boolean isValid(JobBatchRecord record) {
    return record.getAmount() > 0
        && record.getTimeout() > 0
        && record.getType().capacity() > 0
        && record.getWorker().capacity() > 0;
  }

  private void activateJobs(
      TypedRecord<JobBatchRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final JobBatchRecord value = record.getValue();

    final long jobBatchKey = jobKeyGenerator.nextKey();

    final TypedBatchWriter batchWriter = streamWriter.newBatch();
    final AtomicInteger amount = new AtomicInteger(value.getAmount());
    state.forEachActivatableJobs(
        value.getType(),
        (key, jobRecord, control) -> {
          final int remainingAmount = amount.decrementAndGet();
          if (remainingAmount >= 0) {
            final long deadline = currentTimeMillis() + value.getTimeout();
            value.jobKeys().add().setValue(key);
            final JobRecord job = value.jobs().add();

            // clone job record to modify it
            final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(jobRecord.getLength());
            jobRecord.write(buffer, 0);
            job.wrap(buffer);

            // set worker properties on job
            job.setDeadline(deadline).setWorker(value.getWorker());

            // update state and write follow up event for job record
            state.activate(key, job);
            batchWriter.addFollowUpEvent(key, JobIntent.ACTIVATED, job);
          }

          if (remainingAmount < 1) {
            control.stop();
          }
        });

    batchWriter.addFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, value);
    responseWriter.writeEventOnCommand(jobBatchKey, JobBatchIntent.ACTIVATED, value, record);
  }

  private void rejectCommand(
      TypedRecord<JobBatchRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final RejectionType rejectionType;
    final String rejectionReason;

    final JobBatchRecord value = record.getValue();

    if (value.getAmount() < 1) {
      rejectionType = RejectionType.BAD_VALUE;
      rejectionReason = "Job batch amount must be greater than zero, got " + value.getAmount();
    } else if (value.getTimeout() < 1) {
      rejectionType = RejectionType.BAD_VALUE;
      rejectionReason = "Job batch timeout must be greater than zero, got " + value.getTimeout();
    } else if (value.getType().capacity() < 1) {
      rejectionType = RejectionType.BAD_VALUE;
      rejectionReason = "Job batch type must not be empty";
    } else if (value.getWorker().capacity() < 1) {
      rejectionType = RejectionType.BAD_VALUE;
      rejectionReason = "Job batch worker must not be empty";
    } else {
      throw new IllegalStateException("Job batch command is valid and should not be rejected");
    }

    streamWriter.writeRejection(record, rejectionType, rejectionReason);
    responseWriter.writeRejectionOnCommand(record, rejectionType, rejectionReason);
  }
}
