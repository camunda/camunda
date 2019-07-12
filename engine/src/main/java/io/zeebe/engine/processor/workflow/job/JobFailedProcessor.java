/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;

public final class JobFailedProcessor implements TypedRecordProcessor<JobRecord> {

  private static final DirectBuffer DEFAULT_ERROR_MESSAGE = wrapString("No more retries left.");
  private final IncidentRecord incidentEvent = new IncidentRecord();

  @Override
  public void processRecord(
      TypedRecord<JobRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final JobRecord value = event.getValue();

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
          .setWorkflowKey(value.getWorkflowKey())
          .setWorkflowInstanceKey(value.getWorkflowInstanceKey())
          .setElementId(value.getElementIdBuffer())
          .setElementInstanceKey(value.getElementInstanceKey())
          .setJobKey(event.getKey())
          .setVariableScopeKey(value.getElementInstanceKey());

      streamWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
    }
  }
}
