/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

@ExcludeAuthorizationCheck
public final class JobTimeOutProcessor
    implements TypedRecordProcessor<JobRecord>, SuspensionAware<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to time out activated job with key '%d', but %s";
  private final JobState jobState;
  private final SuspensionState suspensionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobProcessingMetrics jobMetrics;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final SuspensionMetrics suspensionMetrics;
  private final InstantSource clock;

  public JobTimeOutProcessor(
      final ProcessingState state,
      final Writers writers,
      final JobProcessingMetrics jobMetrics,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final SuspensionMetrics suspensionMetrics,
      final InstantSource clock) {
    jobState = state.getJobState();
    suspensionState = state.getSuspensionState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.jobMetrics = jobMetrics;
    this.jobActivationBehavior = jobActivationBehavior;
    this.suspensionMetrics = suspensionMetrics;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final var jobKey = record.getKey();
    final var job = jobState.getJob(jobKey);
    final var state = jobState.getState(jobKey);

    if (state == State.ACTIVATED && hasTimedOut(job)) {
      stateWriter.appendFollowUpEvent(jobKey, JobIntent.TIMED_OUT, job);
      jobMetrics.countJobEvent(JobAction.TIMED_OUT, job.getJobKind(), job.getType());

      // TIMED_OUT made the job ACTIVATABLE. If the instance is still SUSPENDED, park it in the same
      // batch so it is not handed out. Use getSuspensionState == SUSPENDED (not isSuspended): while
      // RESUMING the instance is draining and the job must become available again.
      if (suspensionState.getSuspensionState(job.getProcessInstanceKey())
          == SuspensionState.State.SUSPENDED) {
        stateWriter.appendFollowUpEvent(jobKey, JobIntent.SUSPENDED, job);
        suspensionMetrics.jobSuspended();
      } else {
        jobActivationBehavior.notifyJobAvailableAsSideEffect(job);
      }
    } else {
      final var reason =
          switch (state) {
            case ACTIVATED -> "it has not timed out";
            case ACTIVATABLE -> "it must be activated first";
            case FAILED -> "it is marked as failed and is not activated";
            case ERROR_THROWN -> "it has thrown an error and is not activated";
            case WAITING_FOR_SECRET_RESOLUTION ->
                "it is waiting for a secret to be resolved and is not activated";
            case SUSPENDED -> "its process instance is suspended and it is not activated";
            case NOT_FOUND -> "no such job was found";
          };

      final String errorMessage = String.format(NOT_ACTIVATED_JOB_MESSAGE, jobKey, reason);
      rejectionWriter.appendRejection(record, RejectionType.NOT_FOUND, errorMessage);
    }
  }

  private boolean hasTimedOut(final JobRecord job) {
    return job.getDeadline() < clock.millis();
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<JobRecord> record) {
    // Process while suspended: an activated job must leave ACTIVATED on time-out so it can be
    // parked (Job.SUSPENDED) instead of looping on rejected TIME_OUT commands forever.
    return SuspensionBehavior.PROCESS;
  }
}
