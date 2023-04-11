/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String NO_INCIDENT_FOUND_MSG =
      "Expected to resolve incident with key '%d', but no such incident was found";
  private static final String ELEMENT_NOT_IN_SUPPORTED_STATE_MSG =
      "Expected incident to refer to element in state ELEMENT_ACTIVATING or ELEMENT_COMPLETING, but element is in state %s";

  private final ProcessInstanceRecord failedRecord = new ProcessInstanceRecord();

  private final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  private final IncidentState incidentState;
  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final JobState jobState;

  public ResolveIncidentProcessor(
      final ProcessingState processingState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    this.bpmnStreamProcessor = bpmnStreamProcessor;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    incidentState = processingState.getIncidentState();
    elementInstanceState = processingState.getElementInstanceState();
    this.jobActivationBehavior = jobActivationBehavior;
    jobState = processingState.getJobState();
  }

  @Override
  public void processRecord(final TypedRecord<IncidentRecord> command) {
    final long key = command.getKey();

    final var incident = incidentState.getIncidentRecord(key);
    if (incident == null) {
      final var errorMessage = String.format(NO_INCIDENT_FOUND_MSG, key);
      rejectResolveCommand(command, errorMessage, RejectionType.NOT_FOUND);
      return;
    }

    stateWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, incident);
    responseWriter.writeEventOnCommand(key, IncidentIntent.RESOLVED, incident, command);

    final long jobKey = incident.getJobKey();
    final boolean isJobRelatedIncident = jobKey > 0;
    if (isJobRelatedIncident) {
      final JobRecord failedJobRecord = jobState.getJob(jobKey);
      jobActivationBehavior.publishWork(failedJobRecord);
    }

    // if it fails, a new incident is raised
    attemptToContinueProcessProcessing(command, incident);
  }

  private void rejectResolveCommand(
      final TypedRecord<IncidentRecord> command,
      final String errorMessage,
      final RejectionType rejectionType) {

    rejectionWriter.appendRejection(command, rejectionType, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
  }

  private void attemptToContinueProcessProcessing(
      final TypedRecord<IncidentRecord> command, final IncidentRecord incident) {
    final long jobKey = incident.getJobKey();
    final boolean isJobIncident = jobKey > 0;

    if (isJobIncident) {
      return;
    }

    getFailedCommand(incident)
        .ifRightOrLeft(
            failedCommand -> {
              bpmnStreamProcessor.processRecord(failedCommand);
            },
            failure -> {
              final var message =
                  String.format(
                      "Expected to continue processing after incident %d resolved, but failed command not found",
                      command.getKey());
              throw new IllegalStateException(message, new IllegalStateException(failure));
            });
  }

  private Either<String, TypedRecord<ProcessInstanceRecord>> getFailedCommand(
      final IncidentRecord incidentRecord) {
    final long elementInstanceKey = incidentRecord.getElementInstanceKey();
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      return Either.left(
          String.format(
              "Expected to find failed command for element instance %d, but element instance not found",
              elementInstanceKey));
    }
    return getFailedCommandIntent(elementInstance)
        .map(
            commandIntent -> {
              failedRecord.wrap(elementInstance.getValue());
              return new IncidentRecordWrapper(elementInstanceKey, commandIntent, failedRecord);
            });
  }

  private Either<String, ProcessInstanceIntent> getFailedCommandIntent(
      final ElementInstance elementInstance) {
    final var instanceState = elementInstance.getState();
    switch (instanceState) {
      case ELEMENT_ACTIVATING:
        return Either.right(ProcessInstanceIntent.ACTIVATE_ELEMENT);
      case ELEMENT_COMPLETING:
        return Either.right(ProcessInstanceIntent.COMPLETE_ELEMENT);
      default:
        return Either.left(String.format(ELEMENT_NOT_IN_SUPPORTED_STATE_MSG, instanceState));
    }
  }
}
