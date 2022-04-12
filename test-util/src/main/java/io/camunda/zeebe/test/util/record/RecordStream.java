/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class RecordStream extends ExporterRecordStream<RecordValue, RecordStream> {
  public RecordStream(final Stream<Record<RecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected RecordStream supply(final Stream<Record<RecordValue>> wrappedStream) {
    return new RecordStream(wrappedStream);
  }

  public RecordStream between(final long lowerBoundPosition, final long upperBoundPosition) {
    return between(
        r -> r.getPosition() > lowerBoundPosition, r -> r.getPosition() >= upperBoundPosition);
  }

  public RecordStream between(final Record<?> lowerBound, final Record<?> upperBound) {
    return between(lowerBound::equals, upperBound::equals);
  }

  public RecordStream between(
      final Predicate<Record<?>> lowerBound, final Predicate<Record<?>> upperBound) {
    return limit(upperBound::test).skipUntil(lowerBound::test);
  }

  public RecordStream limitToProcessInstance(final long processInstanceKey) {
    return between(
        r ->
            r.getKey() == processInstanceKey
                && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING,
        r ->
            r.getKey() == processInstanceKey
                && Set.of(
                        ProcessInstanceIntent.ELEMENT_COMPLETED,
                        ProcessInstanceIntent.ELEMENT_TERMINATED)
                    .contains(r.getIntent()));
  }

  public ProcessInstanceRecordStream processInstanceRecords() {
    return new ProcessInstanceRecordStream(
        filter(r -> r.getValueType() == ValueType.PROCESS_INSTANCE).map(Record.class::cast));
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

  public JobRecordStream jobRecords() {
    return new JobRecordStream(
        filter(r -> r.getValueType() == ValueType.JOB).map(Record.class::cast));
  }

  public IncidentRecordStream incidentRecords() {
    return new IncidentRecordStream(
        filter(r -> r.getValueType() == ValueType.INCIDENT).map(Record.class::cast));
  }

  public MessageSubscriptionRecordStream messageSubscriptionRecords() {
    return new MessageSubscriptionRecordStream(
        filter(r -> r.getValueType() == ValueType.MESSAGE_SUBSCRIPTION).map(Record.class::cast));
  }

  public ProcessMessageSubscriptionRecordStream processMessageSubscriptionRecords() {
    return new ProcessMessageSubscriptionRecordStream(
        filter(r -> r.getValueType() == ValueType.PROCESS_MESSAGE_SUBSCRIPTION)
            .map(Record.class::cast));
  }
}
