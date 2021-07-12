/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.NoopResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.Either;
import java.util.function.Consumer;

public final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String NO_INCIDENT_FOUND_MSG =
      "Expected to resolve incident with key '%d', but no such incident was found";
  private static final String ELEMENT_NOT_IN_SUPPORTED_STATE_MSG =
      "Expected incident to refer to element in state ELEMENT_ACTIVATING or ELEMENT_COMPLETING, but element is in state %s";

  private final ProcessInstanceRecord failedRecord = new ProcessInstanceRecord();
  private final SideEffectQueue sideEffects = new SideEffectQueue();
  private final TypedResponseWriter noopResponseWriter = new NoopResponseWriter();

  private final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  private final IncidentState incidentState;
  private final ElementInstanceState elementInstanceState;
  private final KeyGenerator keyGenerator;

  public ResolveIncidentProcessor(
      final ZeebeState zeebeState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    this.bpmnStreamProcessor = bpmnStreamProcessor;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    incidentState = zeebeState.getIncidentState();
    elementInstanceState = zeebeState.getElementInstanceState();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    final long key = command.getKey();

    final var incident = incidentState.getIncidentRecord(key);
    if (incident == null) {
      final var errorMessage = String.format(NO_INCIDENT_FOUND_MSG, key);
      rejectResolveCommand(command, responseWriter, errorMessage, RejectionType.NOT_FOUND);
      return;
    }

    stateWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, incident);
    responseWriter.writeEventOnCommand(key, IncidentIntent.RESOLVED, incident, command);

    // if it fails, a new incident is raised
    attemptToContinueProcessProcessing(command, responseWriter, streamWriter, sideEffect, incident);
  }

  private void rejectResolveCommand(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final String errorMessage,
      final RejectionType rejectionType) {

    rejectionWriter.appendRejection(command, rejectionType, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
  }

  private void attemptToContinueProcessProcessing(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect,
      final IncidentRecord incident) {
    final long jobKey = incident.getJobKey();
    final boolean isJobIncident = jobKey > 0;

    if (isJobIncident) {
      return;
    }

    getFailedCommand(incident)
        .ifRightOrLeft(
            failedCommand -> {
              sideEffects.clear();
              sideEffects.add(responseWriter::flush);

              bpmnStreamProcessor.processRecord(
                  failedCommand, noopResponseWriter, streamWriter, sideEffects::add);

              sideEffect.accept(sideEffects);
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
