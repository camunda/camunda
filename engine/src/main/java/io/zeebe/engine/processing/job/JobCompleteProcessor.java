/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class JobCompleteProcessor implements CommandProcessor<JobRecord> {

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard<JobRecord> defaultProcessor;

  public JobCompleteProcessor(final ZeebeState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    defaultProcessor =
        new DefaultJobCommandPreconditionGuard<>("complete", jobState, this::acceptCommand);
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    return defaultProcessor.onCommand(command, commandControl);
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {

    final long jobKey = command.getKey();

    final JobRecord job = jobState.getJob(jobKey);

    job.setVariables(command.getValue().getVariablesBuffer());

    commandControl.accept(JobIntent.COMPLETED, job);
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long key,
      final Intent intent,
      final JobRecord value) {

    final var serviceTaskKey = value.getElementInstanceKey();

    final ElementInstance serviceTask = elementInstanceState.getInstance(serviceTaskKey);

    if (serviceTask != null) {
      final long scopeKey = serviceTask.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        final WorkflowInstanceRecord workflowInstanceRecord = serviceTask.getValue();

        // TODO (#6172) send out COMPLETE_ELEMENT command when service task processor is registered
        // for COMPLETE_ELEMENT commands; switch out for command writer
        stateWriter.appendFollowUpEvent(
            serviceTaskKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, workflowInstanceRecord);
      }
    }
  }
}
