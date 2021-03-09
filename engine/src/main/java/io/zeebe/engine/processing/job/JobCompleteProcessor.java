/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

public final class JobCompleteProcessor implements CommandProcessor<JobRecord> {

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard<JobRecord> defaultProcessor;
  private final TypedEventWriter eventWriter;

  public JobCompleteProcessor(final MutableZeebeState state, final Writers writers) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    defaultProcessor =
        new DefaultJobCommandPreconditionGuard<>("complete", jobState, this::acceptCommand);
    eventWriter = writers.events();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    return defaultProcessor.onCommand(command, commandControl);
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
        final ProcessInstanceRecord processInstanceRecord = serviceTask.getValue();

        // TODO (#6172) send out COMPLETE_ELEMENT command when service task processor is registered
        // for COMPLETE_ELEMENT commands; switch out for command writer
        if (MigratedStreamProcessors.isMigrated(BpmnElementType.SERVICE_TASK)) {
          stateWriter.appendFollowUpEvent(
              serviceTaskKey, ProcessInstanceIntent.ELEMENT_COMPLETING, processInstanceRecord);
        } else {
          eventWriter.appendFollowUpEvent(
              serviceTaskKey, ProcessInstanceIntent.ELEMENT_COMPLETING, processInstanceRecord);
        }
      }
    }
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {

    final long jobKey = command.getKey();

    final JobRecord job = jobState.getJob(jobKey);

    job.setVariables(command.getValue().getVariablesBuffer());

    commandControl.accept(JobIntent.COMPLETED, job);
  }
}
