/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.incident;

import io.zeebe.engine.processing.job.JobErrorThrownProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.zeebe.engine.processing.streamprocessor.writers.NoopResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.JobState.State;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.mutable.MutableIncidentState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import java.util.function.Consumer;

public final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String NO_INCIDENT_FOUND_MSG =
      "Expected to resolve incident with key '%d', but no such incident was found";

  private final SideEffectQueue sideEffects = new SideEffectQueue();
  private final TypedResponseWriter noopResponseWriter = new NoopResponseWriter();

  private final ZeebeState zeebeState;
  private final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor;

  private final IncidentRecordWrapper incidentRecordWrapper = new IncidentRecordWrapper();
  private final JobErrorThrownProcessor jobErrorThrownProcessor;

  public ResolveIncidentProcessor(
      final ZeebeState zeebeState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor) {
    this.bpmnStreamProcessor = bpmnStreamProcessor;
    this.zeebeState = zeebeState;
    jobErrorThrownProcessor = new JobErrorThrownProcessor(zeebeState);
  }

  @Override
  public void processRecord(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    final long incidentKey = command.getKey();
    final MutableIncidentState incidentState = zeebeState.getIncidentState();

    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
    if (incidentRecord != null) {
      incidentState.deleteIncident(incidentKey);

      streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
      responseWriter.writeEventOnCommand(
          incidentKey, IncidentIntent.RESOLVED, incidentRecord, command);

      // process / job is already cleared if canceled, then we simply delete without resolving
      attemptToResolveIncident(responseWriter, streamWriter, sideEffect, incidentRecord);
    } else {
      rejectResolveCommand(command, responseWriter, streamWriter, incidentKey);
    }
  }

  private void rejectResolveCommand(
      final TypedRecord<IncidentRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final long incidentKey) {
    final String errorMessage = String.format(NO_INCIDENT_FOUND_MSG, incidentKey);

    streamWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
  }

  private void attemptToResolveIncident(
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect,
      final IncidentRecord incidentRecord) {
    final long jobKey = incidentRecord.getJobKey();
    final boolean isJobIncident = jobKey > 0;

    if (isJobIncident) {
      attemptToSolveJobIncident(jobKey, streamWriter);
    } else {
      attemptToContinueProcessProcessing(responseWriter, streamWriter, sideEffect, incidentRecord);
    }
  }

  private void attemptToContinueProcessProcessing(
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect,
      final IncidentRecord incidentRecord) {
    final long elementInstanceKey = incidentRecord.getElementInstanceKey();
    final IndexedRecord failedRecord =
        zeebeState.getElementInstanceState().getFailedRecord(elementInstanceKey);

    if (failedRecord != null) {

      sideEffects.clear();
      sideEffects.add(responseWriter::flush);

      incidentRecordWrapper.wrap(failedRecord);
      bpmnStreamProcessor.processRecord(
          incidentRecordWrapper, noopResponseWriter, streamWriter, sideEffects::add);

      sideEffect.accept(sideEffects);
    }
  }

  private void attemptToSolveJobIncident(final long jobKey, final TypedStreamWriter streamWriter) {
    final MutableJobState jobState = zeebeState.getJobState();
    final JobRecord job = jobState.getJob(jobKey);
    final JobState.State state = jobState.getState(jobKey);

    if (state == State.FAILED) {
      // make job activatable again
      jobState.resolve(jobKey, job);

    } else if (state == State.ERROR_THROWN) {

      // try to throw the error again
      jobErrorThrownProcessor.processRecord(jobKey, job, streamWriter);
    }
  }
}
