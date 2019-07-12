/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.incident;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.BpmnStepProcessor;
import io.zeebe.engine.processor.workflow.SideEffectQueue;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import java.util.function.Consumer;

public final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String NO_INCIDENT_FOUND_MSG =
      "Expected to resolve incident with key '%d', but no such incident was found";

  private final BpmnStepProcessor stepProcessor;
  private final ZeebeState zeebeState;
  private final SideEffectQueue queue = new SideEffectQueue();

  public ResolveIncidentProcessor(BpmnStepProcessor stepProcessor, ZeebeState zeebeState) {
    this.stepProcessor = stepProcessor;
    this.zeebeState = zeebeState;
  }

  @Override
  public void processRecord(
      TypedRecord<IncidentRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    final long incidentKey = command.getKey();
    final IncidentState incidentState = zeebeState.getIncidentState();

    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
    if (incidentRecord != null) {
      incidentState.deleteIncident(incidentKey);

      streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
      responseWriter.writeEventOnCommand(
          incidentKey, IncidentIntent.RESOLVED, incidentRecord, command);

      // workflow / job is already cleared if canceled, then we simply delete without resolving
      attemptToResolveIncident(responseWriter, streamWriter, sideEffect, incidentRecord);
    } else {
      rejectResolveCommand(command, responseWriter, streamWriter, incidentKey);
    }
  }

  private void rejectResolveCommand(
      TypedRecord<IncidentRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      long incidentKey) {
    final String errorMessage = String.format(NO_INCIDENT_FOUND_MSG, incidentKey);

    streamWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
  }

  private void attemptToResolveIncident(
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      IncidentRecord incidentRecord) {
    final long jobKey = incidentRecord.getJobKey();
    final boolean isJobIncident = jobKey > 0;

    if (isJobIncident) {
      attemptToMakeJobActivatableAgain(jobKey);
    } else {
      attemptToContinueWorkflowProcessing(responseWriter, streamWriter, sideEffect, incidentRecord);
    }
  }

  private void attemptToContinueWorkflowProcessing(
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      IncidentRecord incidentRecord) {
    final long elementInstanceKey = incidentRecord.getElementInstanceKey();
    final IndexedRecord failedRecord =
        zeebeState.getWorkflowState().getElementInstanceState().getFailedRecord(elementInstanceKey);

    if (failedRecord != null) {

      queue.clear();
      queue.add(responseWriter::flush);
      stepProcessor.processRecordValue(
          failedRecord.getKey(),
          failedRecord.getValue(),
          failedRecord.getState(),
          streamWriter,
          queue::add);

      sideEffect.accept(queue);
    }
  }

  private void attemptToMakeJobActivatableAgain(long jobKey) {
    final JobState jobState = zeebeState.getJobState();
    final JobRecord job = jobState.getJob(jobKey);
    if (job != null) {
      jobState.resolve(jobKey, job);
    }
  }
}
