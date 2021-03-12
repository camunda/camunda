/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processing.common.ErrorEventHandler;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;

@Deprecated // TODO (#6174) delete after refactoring incident processors
public class JobErrorThrownProcessor implements TypedRecordProcessor<JobRecord> {

  private final IncidentRecord incidentEvent = new IncidentRecord();

  private final MutableElementInstanceState elementInstanceState;
  private final MutableJobState jobState;
  private final ErrorEventHandler errorEventHandler;

  public JobErrorThrownProcessor(final MutableZeebeState zeebeState) {
    elementInstanceState = zeebeState.getElementInstanceState();
    jobState = zeebeState.getJobState();
    errorEventHandler =
        new ErrorEventHandler(
            zeebeState.getProcessState(),
            zeebeState.getElementInstanceState(),
            zeebeState.getEventScopeInstanceState(),
            zeebeState.getKeyGenerator());
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
        .setProcessDefinitionKey(job.getProcessDefinitionKey())
        .setProcessInstanceKey(job.getProcessInstanceKey())
        .setElementId(job.getElementIdBuffer())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setJobKey(key)
        .setVariableScopeKey(job.getElementInstanceKey());

    streamWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
  }
}
