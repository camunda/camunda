/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.incident;

import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.zeebe.engine.processing.streamprocessor.writers.NoopResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.IncidentState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.util.Either;
import java.util.function.Consumer;

public final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String NO_INCIDENT_FOUND_MSG =
      "Expected to resolve incident with key '%d', but no such incident was found";
  private static final String RESOLVE_UNHANDLED_ERROR_INCIDENT_MESSAGE =
      "Expected to resolve incident of unhandled error for job %d, but such incidents cannot be resolved. See issue #6000";
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

  public ResolveIncidentProcessor(
      final MutableZeebeState zeebeState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final Writers writers) {
    this.bpmnStreamProcessor = bpmnStreamProcessor;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    incidentState = zeebeState.getIncidentState();
    elementInstanceState = zeebeState.getElementInstanceState();
  }

  @Override
  public void processRecord(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    final long incidentKey = command.getKey();

    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
    if (incidentRecord == null) {
      final var errorMessage = String.format(NO_INCIDENT_FOUND_MSG, incidentKey);
      rejectResolveCommand(command, responseWriter, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
    responseWriter.writeEventOnCommand(
        incidentKey, IncidentIntent.RESOLVED, incidentRecord, command);

    // process / job is already cleared if canceled, then we simply delete without resolving
    attemptToResolveIncident(command, responseWriter, streamWriter, sideEffect, incidentRecord);
  }

  private void rejectResolveCommand(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final String errorMessage) {

    rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
  }

  private void attemptToResolveIncident(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect,
      final IncidentRecord incidentRecord) {
    final long jobKey = incidentRecord.getJobKey();
    final boolean isJobIncident = jobKey > 0;

    if (isJobIncident) {
      // todo (#6000): resolve incident for UNHANDLED_ERROR_EVENT
      // at time of writing the process cannot be fixed, so this incident cannot be resolved
      final var message = String.format(RESOLVE_UNHANDLED_ERROR_INCIDENT_MESSAGE, jobKey);
      rejectResolveCommand(command, responseWriter, message);
    } else {
      attemptToContinueProcessProcessing(
          command, responseWriter, streamWriter, sideEffect, incidentRecord);
    }
  }

  private void attemptToContinueProcessProcessing(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect,
      final IncidentRecord incidentRecord) {

    getFailedCommand(incidentRecord)
        .ifRightOrLeft(
            failedCommand -> {
              sideEffects.clear();
              sideEffects.add(responseWriter::flush);

              bpmnStreamProcessor.processRecord(
                  failedCommand, noopResponseWriter, streamWriter, sideEffects::add);

              sideEffect.accept(sideEffects);
            },
            failure -> rejectResolveCommand(command, responseWriter, failure));
  }

  private Either<String, TypedRecord<ProcessInstanceRecord>> getFailedCommand(
      final IncidentRecord incidentRecord) {
    final long elementInstanceKey = incidentRecord.getElementInstanceKey();
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      return Either.left(
          String.format(
              "Expected to resolve incident with element instance %d, but element instance not found",
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
    if (!MigratedStreamProcessors.isMigrated(elementInstance.getValue().getBpmnElementType())) {
      return Either.right(instanceState);
    }
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
