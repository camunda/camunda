/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.stream.Stream;

public final class IncidentRecordStream
    extends ExporterRecordStream<IncidentRecordValue, IncidentRecordStream> {

  public IncidentRecordStream(final Stream<Record<IncidentRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected IncidentRecordStream supply(final Stream<Record<IncidentRecordValue>> wrappedStream) {
    return new IncidentRecordStream(wrappedStream);
  }

  public IncidentRecordStream withErrorType(final ErrorType errorType) {
    return valueFilter(v -> errorType.equals(v.getErrorType()));
  }

  public IncidentRecordStream withErrorMessage(final String errorMessage) {
    return valueFilter(v -> errorMessage.equals(v.getErrorMessage()));
  }

  public IncidentRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public IncidentRecordStream withElementId(final String elementId) {
    return valueFilter(v -> elementId.equals(v.getElementId()));
  }

  public IncidentRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public IncidentRecordStream withJobKey(final long jobKey) {
    return valueFilter(v -> v.getJobKey() == jobKey);
  }
}
