/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

@ExcludeAuthorizationCheck
public final class JobYieldProcessor
    implements TypedRecordProcessor<JobRecord>, SuspensionAware<JobRecord> {
  private final JobState jobState;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobCommandPreconditionValidator preconditionChecker;

  public JobYieldProcessor(
      final ProcessingState state,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final CslTenantCheck tenantCheck) {
    jobState = state.getJobState();
    jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    preconditionChecker =
        new JobCommandPreconditionValidator(
            jobState,
            state.getBannedInstanceState(),
            "yield",
            List.of(State.ACTIVATED),
            tenantCheck);
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final var jobKey = record.getKey();
    preconditionChecker
        .check(record)
        .ifRightOrLeft(
            yieldedJob -> {
              stateWriter.appendFollowUpEvent(jobKey, JobIntent.YIELDED, yieldedJob);
              jobActivationBehavior.notifyJobAvailableAsSideEffect(yieldedJob);
            },
            rejection ->
                rejectionWriter.appendRejection(record, rejection.type(), rejection.reason()));
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<JobRecord> record) {
    // YIELD is an internal command (written by the job-stream error handler when a client is
    // blocked); buffer it while suspended so the yield is applied once the instance resumes instead
    // of being lost to a rejection.
    return SuspensionBehavior.BUFFER;
  }
}
