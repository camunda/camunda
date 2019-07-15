/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.function.Predicate;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class RecordStream extends ExporterRecordStream<RecordValue, RecordStream> {
  public RecordStream(Stream<Record<RecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected RecordStream supply(Stream<Record<RecordValue>> wrappedStream) {
    return new RecordStream(wrappedStream);
  }

  public RecordStream between(long lowerBoundPosition, long upperBoundPosition) {
    return between(
        r -> r.getPosition() > lowerBoundPosition, r -> r.getPosition() >= upperBoundPosition);
  }

  public RecordStream between(Record<?> lowerBound, Record<?> upperBound) {
    return between(lowerBound::equals, upperBound::equals);
  }

  public RecordStream between(Predicate<Record<?>> lowerBound, Predicate<Record<?>> upperBound) {
    return limit(upperBound::test).skipUntil(lowerBound::test);
  }

  public RecordStream limitToWorkflowInstance(long workflowInstanceKey) {
    return between(
        r ->
            r.getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATING
                && r.getKey() == workflowInstanceKey,
        r ->
            r.getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED
                && r.getKey() == workflowInstanceKey);
  }

  public WorkflowInstanceRecordStream workflowInstanceRecords() {
    return new WorkflowInstanceRecordStream(
        filter(r -> r.getValueType() == ValueType.WORKFLOW_INSTANCE).map(Record.class::cast));
  }

  public TimerRecordStream timerRecords() {
    return new TimerRecordStream(
        filter(r -> r.getValueType() == ValueType.TIMER).map(Record.class::cast));
  }

  public VariableDocumentRecordStream variableDocumentRecords() {
    return new VariableDocumentRecordStream(
        filter(r -> r.getValueType() == ValueType.VARIABLE_DOCUMENT).map(Record.class::cast));
  }

  public VariableRecordStream variableRecords() {
    return new VariableRecordStream(
        filter(r -> r.getValueType() == ValueType.VARIABLE).map(Record.class::cast));
  }
}
