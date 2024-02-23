/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
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

public final class JobYieldProcessor implements TypedRecordProcessor<JobRecord> {
  private final JobState jobState;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobCommandPreconditionChecker preconditionChecker;

  public JobYieldProcessor(
      final ProcessingState state, final BpmnBehaviors bpmnBehaviors, final Writers writers) {
    jobState = state.getJobState();
    jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    preconditionChecker = new JobCommandPreconditionChecker("yield", List.of(State.ACTIVATED));
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    preconditionChecker
        .check(state, jobKey)
        .ifRightOrLeft(
            ok -> {
              final JobRecord yieldedJob = jobState.getJob(jobKey);

              stateWriter.appendFollowUpEvent(jobKey, JobIntent.YIELDED, yieldedJob);
              jobActivationBehavior.notifyJobAvailableAsSideEffect(yieldedJob);
            },
            violation ->
                rejectionWriter.appendRejection(record, violation.getLeft(), violation.getRight()));
  }
}
