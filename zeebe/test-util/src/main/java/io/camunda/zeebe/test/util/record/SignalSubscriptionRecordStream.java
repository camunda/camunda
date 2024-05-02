/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import java.util.stream.Stream;

public final class SignalSubscriptionRecordStream
    extends ExporterRecordStream<SignalSubscriptionRecordValue, SignalSubscriptionRecordStream> {

  public SignalSubscriptionRecordStream(
      final Stream<Record<SignalSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected SignalSubscriptionRecordStream supply(
      final Stream<Record<SignalSubscriptionRecordValue>> wrappedStream) {
    return new SignalSubscriptionRecordStream((wrappedStream));
  }

  public SignalSubscriptionRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public SignalSubscriptionRecordStream withProcessDefinitionKey(final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public SignalSubscriptionRecordStream withCatchEventId(final String catchEventId) {
    return valueFilter(v -> catchEventId.equals(v.getCatchEventId()));
  }

  public SignalSubscriptionRecordStream withCatchEventInstanceKey(
      final long catchEventInstanceKey) {
    return valueFilter(v -> v.getCatchEventInstanceKey() == catchEventInstanceKey);
  }

  public SignalSubscriptionRecordStream withSignalName(final String signalName) {
    return valueFilter(v -> signalName.equals(v.getSignalName()));
  }
}
