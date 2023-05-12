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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class JobYieldProcessor implements TypedRecordProcessor<JobRecord> {

  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to yield activated job with key '%d', but %s";
  private final JobState jobState;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public JobYieldProcessor(
      final ProcessingState state, final BpmnBehaviors bpmnBehaviors, final Writers writers) {
    jobState = state.getJobState();
    jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long key = record.getKey();
    final JobState.State state = jobState.getState(key);
    if (state == State.ACTIVATED) {
      final JobRecord yieldedJob = record.getValue();

      stateWriter.appendFollowUpEvent(key, JobIntent.YIELDED, yieldedJob);
      jobActivationBehavior.notifyJobAvailableAsSideEffect(yieldedJob);
    } else {
      final String textState;

      switch (state) {
        case ACTIVATABLE:
          textState = "it must be activated first";
          break;
        case FAILED:
          textState = "it is marked as failed";
          break;
        default:
          textState = "no such job was found";
          break;
      }

      rejectionWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(NOT_ACTIVATED_JOB_MESSAGE, key, textState));
    }
  }
}
