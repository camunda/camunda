/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.job.processing;

import io.camunda.zeebe.engine.common.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.common.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.common.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.job.state.immutable.JobState;
import io.camunda.zeebe.engine.job.state.immutable.JobState.State;
import io.camunda.zeebe.engine.common.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

@ExcludeAuthorizationCheck
public final class JobYieldProcessor implements TypedRecordProcessor<JobRecord> {
  private final JobState jobState;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobCommandPreconditionChecker preconditionChecker;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;

  public JobYieldProcessor(
      final ProcessingState state,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final AuthorizationCheckBehavior authorizationCheckBehavior) {
    jobState = state.getJobState();
    jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.authorizationCheckBehavior = authorizationCheckBehavior;
    preconditionChecker =
        new JobCommandPreconditionChecker(
            jobState, "yield", List.of(State.ACTIVATED), authorizationCheckBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    preconditionChecker
        .check(state, record)
        .ifRightOrLeft(
            yieldedJob -> {
              stateWriter.appendFollowUpEvent(jobKey, JobIntent.YIELDED, yieldedJob);
              jobActivationBehavior.notifyJobAvailableAsSideEffect(yieldedJob);
            },
            rejection ->
                rejectionWriter.appendRejection(record, rejection.type(), rejection.reason()));
  }
}
