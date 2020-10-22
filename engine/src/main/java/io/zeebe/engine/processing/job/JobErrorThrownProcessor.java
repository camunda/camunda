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
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;

public class JobErrorThrownProcessor implements TypedRecordProcessor<JobRecord> {

  private final IncidentRecord incidentEvent = new IncidentRecord();

  private final ElementInstanceState elementInstanceState;
  private final JobState jobState;
  private final ErrorEventHandler errorEventHandler;

  public JobErrorThrownProcessor(
      final WorkflowState workflowState, final KeyGenerator keyGenerator, final JobState jobState) {
    elementInstanceState = workflowState.getElementInstanceState();
    this.jobState = jobState;
    errorEventHandler = new ErrorEventHandler(workflowState, keyGenerator);
  }

  @Override
  public void processRecord(
      final TypedRecord<JobRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final var job = record.getValue();
    final var jobKey = record.getKey();

    processRecord(jobKey, job, streamWriter);
  }

  public void processRecord(
      final long jobKey, final JobRecord job, final TypedStreamWriter streamWriter) {
    final var serviceTaskInstanceKey = job.getElementInstanceKey();
    final var serviceTaskInstance = elementInstanceState.getInstance(serviceTaskInstanceKey);

    if (serviceTaskInstance != null && serviceTaskInstance.isActive()) {

      final var errorCode = job.getErrorCodeBuffer();

      final boolean errorThrown =
          errorEventHandler.throwErrorEvent(errorCode, serviceTaskInstance, streamWriter);
      if (errorThrown) {

        // remove job reference to not cancel it while terminating the task
        serviceTaskInstance.setJobKey(-1L);
        elementInstanceState.updateInstance(serviceTaskInstance);

        jobState.delete(jobKey, job);

      } else {
        raiseIncident(jobKey, job, streamWriter);
      }
    }
  }

  private void raiseIncident(
      final long key, final JobRecord job, final TypedStreamWriter streamWriter) {

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

    streamWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
  }
}
