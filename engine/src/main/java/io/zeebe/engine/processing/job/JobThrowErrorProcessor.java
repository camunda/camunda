/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.analyzers.CatchEventAnalyzer;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;

public class JobThrowErrorProcessor implements CommandProcessor<JobRecord> {

  /**
   * Marker element ID. This ID is used to indicate that a given catch event could not be found. The
   * marker ID is used to prevent repeated catch event lookups, which is an expensive operation
   * (particularly when no catch event can be found)
   */
  public static final String NO_CATCH_EVENT_FOUND = "NO_CATCH_EVENT_FOUND";

  private final IncidentRecord incidentEvent = new IncidentRecord();
  private final ProcessInstanceRecord eventOccurredRecord = new ProcessInstanceRecord();

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard<JobRecord> defaultProcessor;
  private final CatchEventAnalyzer stateAnalyzer;
  private final KeyGenerator keyGenerator;
  private final EventScopeInstanceState eventScopeInstanceState;

  public JobThrowErrorProcessor(final ZeebeState state, final KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    eventScopeInstanceState = state.getEventScopeInstanceState();

    defaultProcessor =
        new DefaultJobCommandPreconditionGuard<>(
            "throw an error for", jobState, this::acceptCommand);

    stateAnalyzer = new CatchEventAnalyzer(state.getProcessState(), elementInstanceState);
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
      final long jobKey,
      final Intent intent,
      final JobRecord job) {

    final var serviceTaskInstanceKey = job.getElementId();

    if (NO_CATCH_EVENT_FOUND.equals(serviceTaskInstanceKey)) {
      raiseIncident(jobKey, job, commandWriter);
      return;
    }

    final var serviceTaskInstance = elementInstanceState.getInstance(job.getElementInstanceKey());
    final var errorCode = job.getErrorCodeBuffer();

    final var foundCatchEvent = stateAnalyzer.findCatchEvent(errorCode, serviceTaskInstance);
    // TODO (#6472) send TERMINATE_ELEMENT command instead
    writeEventOccurredRecord(stateWriter, foundCatchEvent);
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();

    final JobRecord job = jobState.getJob(jobKey);
    job.setErrorCode(command.getValue().getErrorCodeBuffer());
    job.setErrorMessage(command.getValue().getErrorMessageBuffer());

    final var serviceTaskInstanceKey = job.getElementInstanceKey();
    final var serviceTaskInstance = elementInstanceState.getInstance(serviceTaskInstanceKey);

    final var errorCode = job.getErrorCodeBuffer();
    final var foundCatchEvent = stateAnalyzer.findCatchEvent(errorCode, serviceTaskInstance);

    if (foundCatchEvent == null) {
      job.setElementId(NO_CATCH_EVENT_FOUND);

      commandControl.accept(JobIntent.ERROR_THROWN, job);
    } else if (!serviceTaskInstanceIsActive(serviceTaskInstance)) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          "Expected to find active service task, but was " + serviceTaskInstance);
    } else if (!eventScopeInstanceState.isAcceptingEvent(
        foundCatchEvent.getElementInstance().getKey())) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          "Expected to find event scope that is accepting events, but was "
              + foundCatchEvent.getElementInstance());
    } else {
      commandControl.accept(JobIntent.ERROR_THROWN, job);
    }
  }

  private boolean serviceTaskInstanceIsActive(final ElementInstance serviceTaskInstance) {
    return serviceTaskInstance != null && serviceTaskInstance.isActive();
  }

  private void writeEventOccurredRecord(
      final StateWriter stateWriter, final CatchEventAnalyzer.CatchEventTuple foundCatchEvent) {
    final var eventScopeInstance = foundCatchEvent.getElementInstance();
    final var catchEvent = foundCatchEvent.getCatchEvent();

    eventOccurredRecord.wrap(eventScopeInstance.getValue());

    final long eventOccurredKey;
    if (isEventSubprocess(catchEvent)) {
      eventOccurredKey = keyGenerator.nextKey();
      eventOccurredRecord
          .setElementId(catchEvent.getId())
          .setBpmnElementType(BpmnElementType.START_EVENT)
          .setFlowScopeKey(eventScopeInstance.getKey());
    } else {
      eventOccurredKey = eventScopeInstance.getKey();
    }

    stateWriter.appendFollowUpEvent(
        eventOccurredKey, ProcessInstanceIntent.EVENT_OCCURRED, eventOccurredRecord);
  }

  private boolean isEventSubprocess(final ExecutableFlowElement catchEvent) {
    return catchEvent instanceof ExecutableStartEvent
        && ((ExecutableStartEvent) catchEvent).getEventSubProcess() != null;
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
        .setProcessDefinitionKey(job.getProcessDefinitionKey())
        .setProcessInstanceKey(job.getProcessInstanceKey())
        .setElementId(job.getElementIdBuffer())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setJobKey(key)
        .setVariableScopeKey(job.getElementInstanceKey());

    commandWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
  }
}
