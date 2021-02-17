/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processing.common.ErrorEventHandler;
import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;

public class JobThrowErrorProcessor implements CommandProcessor<JobRecord> {
  private final IncidentRecord incidentEvent = new IncidentRecord();

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard<JobRecord> defaultProcessor;
  private final ErrorEventHandler errorEventHandler;

  public JobThrowErrorProcessor(final ZeebeState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    errorEventHandler =
        new ErrorEventHandler(
            state.getWorkflowState(),
            state.getElementInstanceState(),
            state.getEventScopeInstanceState(),
            state.getKeyGenerator());

    defaultProcessor =
        new DefaultJobCommandPreconditionGuard<>(
            "throw an error for", jobState, this::acceptCommand);
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
    job.setErrorCode(command.getValue().getErrorCodeBuffer());
    job.setErrorMessage(command.getValue().getErrorMessageBuffer());

    commandControl.accept(JobIntent.ERROR_THROWN, job);
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long jobKey,
      final Intent intent,
      final JobRecord job) {

    final var serviceTaskInstanceKey = job.getElementInstanceKey();
    final var serviceTaskInstance = elementInstanceState.getInstance(serviceTaskInstanceKey);

    if (serviceTaskInstance != null && serviceTaskInstance.isActive()) {

      final var errorCode = job.getErrorCodeBuffer();

      final boolean errorThrown =
          errorEventHandler.throwErrorEvent(errorCode, serviceTaskInstance, stateWriter);
      if (!errorThrown) {
        raiseIncident(jobKey, job, commandWriter);
      }
    }
  }

  private void raiseIncident(
      final long key, final JobRecord job, final TypedCommandWriter commandWriter) {

    final DirectBuffer jobErrorMessage = job.getErrorMessageBuffer();
    DirectBuffer incidentErrorMessage =
        wrapString(
            String.format(
                "An error was thrown with the code '%s' but not caught.", job.getErrorCode()));
    if (jobErrorMessage.capacity() > 0) {
      incidentErrorMessage = jobErrorMessage;
    }

    incidentEvent.reset();
    incidentEvent
        .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .setErrorMessage(incidentErrorMessage)
        .setBpmnProcessId(job.getBpmnProcessIdBuffer())
        .setWorkflowKey(job.getWorkflowKey())
        .setWorkflowInstanceKey(job.getWorkflowInstanceKey())
        .setElementId(job.getElementIdBuffer())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setJobKey(key)
        .setVariableScopeKey(job.getElementInstanceKey());

    commandWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
  }
}
