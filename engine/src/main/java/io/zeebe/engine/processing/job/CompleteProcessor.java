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
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class CompleteProcessor implements CommandProcessor<JobRecord> {

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandProcessor<JobRecord> defaultProcessor;
  private final StateWriter stateWriter;

  public CompleteProcessor(final ZeebeState state, final StateWriter stateWriter) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    defaultProcessor = new DefaultJobCommandProcessor<>("complete", jobState, this::acceptCommand);
    this.stateWriter = stateWriter;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    return defaultProcessor.onCommand(command, commandControl);
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {

    final JobRecord job = sendJobCompletedEvent(command, commandControl);

    sendElementCompletingEvent(job.getElementInstanceKey());
  }

  private JobRecord sendJobCompletedEvent(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();

    final JobRecord job = jobState.getJob(jobKey);

    job.setVariables(command.getValue().getVariablesBuffer());

    commandControl.accept(JobIntent.COMPLETED, job);

    return job;
  }

  private void sendElementCompletingEvent(final long elementInstanceKey) {
    final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

    if (elementInstance != null) {
      final long scopeKey = elementInstance.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        final WorkflowInstanceRecord value = elementInstance.getValue();

        // TODO send out COMPLETE_ELEMENT command when available, rename method; switch out for
        // command writer
        stateWriter.appendFollowUpEvent(
            elementInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, value);
      }
    }
  }
}
