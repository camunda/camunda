/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.job.JobVariablesCollector;
import io.camunda.zeebe.engine.processing.streamprocessor.JobActivationProperties;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer.JobStream;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A behavior class which allows processors to activate a job. Use this anywhere a job should
 * become activated and processed by a job worker.
 *
 * This behavior class will either push a job on a {@link io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer.JobStream}
 * or notify job workers that a job of a given type is available for processing. If a <code>JobStream/code>
 * is available for a job with a given type, the job will be pushed on the <code>JobStream/code>. If
 * no <code>JobStream/code> is available for the given job type, a notification is used.
 *
 * Both the job push and the job worker notification are executed through a {@link io.camunda.zeebe.stream.api.SideEffectProducer}.
 */
public class BpmnJobActivationBehavior {
  private final JobStreamer jobStreamer;
  private final JobVariablesCollector jobVariablesCollector;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final KeyGenerator keyGenerator;
  private final JobMetrics jobMetrics;

  public BpmnJobActivationBehavior(
      final JobStreamer jobStreamer,
      final VariableState variableState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final JobMetrics jobMetrics) {
    this.jobStreamer = jobStreamer;
    this.keyGenerator = keyGenerator;
    this.jobMetrics = jobMetrics;
    jobVariablesCollector = new JobVariablesCollector(variableState);
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
  }

  public void publishWork(final long jobKey, final JobRecord jobRecord) {
    final JobRecord wrappedJobRecord = new JobRecord();
    wrappedJobRecord.wrapWithoutVariables(jobRecord);

    final String jobType = wrappedJobRecord.getType();
    final Optional<JobStream> optionalJobStream =
        jobStreamer.streamFor(wrappedJobRecord.getTypeBuffer());

    if (optionalJobStream.isPresent()) {
      final JobStream jobStream = optionalJobStream.get();
      final JobActivationProperties properties = jobStream.properties();

      // we push the job immediately, so the deadline is always calculated from the current time
      final var deadline = ActorClock.currentTimeMillis() + properties.timeout();
      wrappedJobRecord.setDeadline(deadline);
      wrappedJobRecord.setWorker(properties.worker());

      // reuse the existing JobBatch activation mechanism
      final JobBatchRecord jobBatchRecord = new JobBatchRecord();
      jobBatchRecord
          .setType(jobType)
          .setTimeout(properties.timeout())
          .setWorker(properties.worker());
      appendJobToBatch(jobBatchRecord, jobKey, wrappedJobRecord);
      final var jobBatchKey = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(jobBatchKey, JobBatchIntent.ACTIVATED, jobBatchRecord);

      jobVariablesCollector.setJobVariables(
          properties.fetchVariables(), wrappedJobRecord, wrappedJobRecord.getElementInstanceKey());
      final var pushableJobRecord = new JobRecord();
      cloneJob(wrappedJobRecord, pushableJobRecord);
      final var activatedJob = new ActivatedJobImpl();
      activatedJob.setJobKey(jobKey).setRecord(pushableJobRecord);

      // job push through side effect
      sideEffectWriter.appendSideEffect(
          () -> {
            jobStream.push(activatedJob);
            jobMetrics.jobPush(jobType);
            return true;
          });
    } else {
      notifyJobAvailable(jobType);
    }
  }

  public void notifyJobAvailableAsSideEffect(final JobRecord jobRecord) {
    final String jobType = jobRecord.getType();
    notifyJobAvailable(jobType);
  }

  private void notifyJobAvailable(final String jobType) {
    sideEffectWriter.appendSideEffect(
        () -> {
          jobStreamer.notifyWorkAvailable(jobType);
          jobMetrics.jobNotification(jobType);
          return true;
        });
  }

  private void appendJobToBatch(
      final JobBatchRecord jobBatchRecord, final Long jobKey, final JobRecord jobRecord) {

    // we don't need to clone the job record, as the buffer isn't reused
    jobBatchRecord.jobKeys().add().setValue(jobKey);
    jobBatchRecord.jobs().add().wrapWithoutVariables(jobRecord);
  }

  private void cloneJob(final JobRecord jobRecord, final JobRecord jobRecordClone) {
    final var bytes = new byte[jobRecord.getLength()];
    final var jobCopyBuffer = new UnsafeBuffer(bytes);
    jobRecord.write(jobCopyBuffer, 0);
    jobRecordClone.wrap(jobCopyBuffer, 0, jobRecord.getLength());
  }
}
