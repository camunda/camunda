/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceSuspensionJobBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

@ExcludeAuthorizationCheck
public final class JobYieldProcessor
    implements TypedRecordProcessor<JobRecord>, SuspensionAware<JobRecord> {
  private final JobState jobState;
  private final SuspensionState suspensionState;
  private final ProcessInstanceSuspensionJobBehavior suspensionJobBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final SuspensionMetrics suspensionMetrics;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobCommandPreconditionValidator preconditionChecker;

  public JobYieldProcessor(
      final ProcessingState state,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final CslTenantCheck tenantCheck,
      final SuspensionMetrics suspensionMetrics) {
    jobState = state.getJobState();
    suspensionState = state.getSuspensionState();
    suspensionJobBehavior =
        new ProcessInstanceSuspensionJobBehavior(
            state.getElementInstanceState(), state.getJobState());
    this.suspensionMetrics = suspensionMetrics;
    jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    preconditionChecker =
        new JobCommandPreconditionValidator(
                jobState,
                state.getBannedInstanceState(),
                "yield",
                List.of(State.ACTIVATED),
                tenantCheck)
            .skipTenantFilterForInternalCommands();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final var jobKey = record.getKey();
    preconditionChecker
        .check(record)
        .ifRightOrLeft(
            yieldedJob -> {
              stateWriter.appendFollowUpEvent(jobKey, JobIntent.YIELDED, yieldedJob);

              // park while suspending or suspended; RESUMING must release it
              final var marker =
                  suspensionState.getSuspensionState(yieldedJob.getProcessInstanceKey());
              if ((marker == SuspensionState.State.SUSPENDING
                      || marker == SuspensionState.State.SUSPENDED)
                  && !suspensionJobBehavior.isExemptFromSuspension(yieldedJob)) {
                stateWriter.appendFollowUpEvent(jobKey, JobIntent.SUSPENDED, yieldedJob);
                suspensionMetrics.jobSuspended();
              } else {
                jobActivationBehavior.notifyJobAvailableAsSideEffect(yieldedJob);
              }
            },
            rejection ->
                rejectionWriter.appendRejection(record, rejection.type(), rejection.reason()));
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<JobRecord> record) {
    // Process while suspended and park the job, as for time-out. A buffered yield could drain
    // after the job was handed out again during the resume and take it from its new worker.
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<JobRecord> record) {
    return SuspensionAction.PROCESS;
  }
}
