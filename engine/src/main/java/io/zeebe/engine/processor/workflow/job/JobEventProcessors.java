/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.job;

import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;

public class JobEventProcessors {
  public static void addJobProcessors(
      TypedRecordProcessors typedRecordProcessors, ZeebeState zeebeState) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final JobState jobState = zeebeState.getJobState();

    typedRecordProcessors
        .onEvent(ValueType.JOB, JobIntent.CREATED, new JobCreatedProcessor(workflowState))
        .onEvent(ValueType.JOB, JobIntent.COMPLETED, new JobCompletedEventProcessor(workflowState))
        .onCommand(ValueType.JOB, JobIntent.CREATE, new CreateProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.FAIL, new FailProcessor(jobState))
        .onEvent(ValueType.JOB, JobIntent.FAILED, new JobFailedProcessor())
        .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new TimeOutProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelProcessor(jobState))
        .onCommand(
            ValueType.JOB_BATCH,
            JobBatchIntent.ACTIVATE,
            new JobBatchActivateProcessor(
                jobState,
                workflowState.getElementInstanceState().getVariablesState(),
                zeebeState.getKeyGenerator()))
        .withListener(new JobTimeoutTrigger(jobState));
  }
}
