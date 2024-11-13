/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public final class IncidentResolveProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String NO_RETRIES_LEFT_MSG =
      "Expected to resolve incident with key '%d', but job with key '%d' has no retries left. Please update the job retries and retry resolving the incident";
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
  private final AuthorizationCheckBehavior authCheckBehavior;

  public IncidentResolveProcessor(
      final ProcessingState processingState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.bpmnStreamProcessor = bpmnStreamProcessor;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    incidentState = processingState.getIncidentState();
    elementInstanceState = processingState.getElementInstanceState();
    this.jobActivationBehavior = jobActivationBehavior;
    jobState = processingState.getJobState();
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<IncidentRecord> command) {
    final long key = command.getKey();

    final var incident = incidentState.getIncidentRecord(key, command.getAuthorizations());
    if (incident == null) {
      final var errorMessage = String.format(NO_INCIDENT_FOUND_MSG, key);
      rejectResolveCommand(command, errorMessage, RejectionType.NOT_FOUND);
      return;
    }

    final var authRequest =
        new AuthorizationRequest(
                command, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .addResourceId(incident.getBpmnProcessId());
    if (!authCheckBehavior.isAuthorized(authRequest)) {
      final var reason =
          UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
              authRequest.getPermissionType(),
              authRequest.getResourceType(),
              "BPMN process id '%s'".formatted(incident.getBpmnProcessId()));
      rejectResolveCommand(command, reason, RejectionType.UNAUTHORIZED);
      return;
    }

    final long jobKey = incident.getJobKey();
    if (isJobRelatedIncident(jobKey) && jobState.getJob(jobKey).getRetries() <= 0) {
      final var errorMessage = String.format(NO_RETRIES_LEFT_MSG, key, jobKey);
      rejectResolveCommand(command, errorMessage, RejectionType.INVALID_STATE);
      return;
    }

    stateWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, incident);
    responseWriter.writeEventOnCommand(key, IncidentIntent.RESOLVED, incident, command);

    publishIncidentRelatedJob(jobKey);

    // if it fails, a new incident is raised
    attemptToContinueProcessProcessing(command, incident);
  }

  private void rejectResolveCommand(
      final TypedRecord<IncidentRecord> command,
      final String errorMessage,
      final RejectionType rejectionType) {

    rejectionWriter.appendRejection(command, rejectionType, errorMessage);
    responseWriter.writeRejectionOnCommand(command, rejectionType, errorMessage);
  }

  private void attemptToContinueProcessProcessing(
      final TypedRecord<IncidentRecord> command, final IncidentRecord incident) {
    final long jobKey = incident.getJobKey();

    if (isJobRelatedIncident(jobKey)) {
      return;
    }

    getFailedCommand(incident)
        .ifRightOrLeft(
            bpmnStreamProcessor::processRecord,
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

  private void publishIncidentRelatedJob(final long jobKey) {
    if (isJobRelatedIncident(jobKey)) {
      final JobRecord failedJobRecord = jobState.getJob(jobKey);
      jobActivationBehavior.publishWork(jobKey, failedJobRecord);
    }
  }

  private static boolean isJobRelatedIncident(final long jobKey) {
    return jobKey > 0;
  }
}
