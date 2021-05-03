/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.ZeebeState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;

public final class JobFailProcessor implements CommandProcessor<JobRecord> {

  private static final DirectBuffer DEFAULT_ERROR_MESSAGE = wrapString("No more retries left.");
  private final IncidentRecord incidentEvent = new IncidentRecord();

  private final JobState jobState;
  private final DefaultJobCommandPreconditionGuard<JobRecord> defaultProcessor;
  private final KeyGenerator keyGenerator;

  public JobFailProcessor(final ZeebeState state, final KeyGenerator keyGenerator) {
    jobState = state.getJobState();
    this.keyGenerator = keyGenerator;
    defaultProcessor =
        new DefaultJobCommandPreconditionGuard<>("fail", jobState, this::acceptCommand);
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
    if (value.getRetries() <= 0) {
      final DirectBuffer jobErrorMessage = value.getErrorMessageBuffer();
      DirectBuffer incidentErrorMessage = DEFAULT_ERROR_MESSAGE;
      if (jobErrorMessage.capacity() > 0) {
        incidentErrorMessage = jobErrorMessage;
      }

      incidentEvent.reset();
      incidentEvent
          .setErrorType(ErrorType.JOB_NO_RETRIES)
          .setErrorMessage(incidentErrorMessage)
          .setBpmnProcessId(value.getBpmnProcessIdBuffer())
          .setProcessDefinitionKey(value.getProcessDefinitionKey())
          .setProcessInstanceKey(value.getProcessInstanceKey())
          .setElementId(value.getElementIdBuffer())
          .setElementInstanceKey(value.getElementInstanceKey())
          .setJobKey(key)
          .setVariableScopeKey(value.getElementInstanceKey());

      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
    }
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long key = command.getKey();
    final JobRecord failedJob = jobState.getJob(key);
    failedJob.setRetries(command.getValue().getRetries());
    failedJob.setErrorMessage(command.getValue().getErrorMessageBuffer());
    commandControl.accept(JobIntent.FAILED, failedJob);
  }
}
