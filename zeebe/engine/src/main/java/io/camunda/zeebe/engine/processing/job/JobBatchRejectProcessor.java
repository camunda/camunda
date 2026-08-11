/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobBatchDeliveryState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rejects a pending JobBatch delivery: yields still-activated jobs so they become activatable
 * again, then clears the pending delivery. Idempotent when no pending delivery remains.
 */
@ExcludeAuthorizationCheck
public final class JobBatchRejectProcessor implements TypedRecordProcessor<JobBatchRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(JobBatchRejectProcessor.class);

  private final JobBatchDeliveryState jobBatchDeliveryState;
  private final JobState jobState;
  private final StateWriter stateWriter;
  private final BpmnJobActivationBehavior jobActivationBehavior;

  public JobBatchRejectProcessor(
      final ProcessingState state,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    jobBatchDeliveryState = state.getJobBatchDeliveryState();
    jobState = state.getJobState();
    stateWriter = writers.state();
    this.jobActivationBehavior = jobActivationBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<JobBatchRecord> record) {
    final var value = record.getValue();
    final long attemptKey = value.getDeliveryAttemptKey();
    if (attemptKey <= 0) {
      // Nothing to reject; still emit REJECTED for a deterministic command completion.
      stateWriter.appendFollowUpEvent(record.getKey(), JobBatchIntent.REJECTED, value);
      return;
    }

    final var pending = jobBatchDeliveryState.getPendingDelivery(attemptKey);
    if (pending.isEmpty()) {
      stateWriter.appendFollowUpEvent(record.getKey(), JobBatchIntent.REJECTED, value);
      return;
    }

    final var delivery = pending.get();
    final var eventValue = new JobBatchRecord();
    eventValue.setType(delivery.getType());
    eventValue.setDeliveryAttemptKey(attemptKey);
    eventValue.setDeliveryDeadline(delivery.getDeliveryDeadline());

    for (final long jobKey : delivery.getJobKeys()) {
      eventValue.jobKeys().add().setValue(jobKey);
      if (jobState.getState(jobKey) != State.ACTIVATED) {
        continue;
      }
      final JobRecord job = jobState.getJob(jobKey);
      if (job == null) {
        continue;
      }
      stateWriter.appendFollowUpEvent(jobKey, JobIntent.YIELDED, job);
      jobActivationBehavior.notifyJobAvailableAsSideEffect(job);
    }

    LOG.warn(
        "Rejected JobBatch delivery for type {} attempt {} with job keys {}; jobs yielded for re-activation",
        delivery.getType(),
        attemptKey,
        delivery.getJobKeys());

    stateWriter.appendFollowUpEvent(record.getKey(), JobBatchIntent.REJECTED, eventValue);
  }
}
