/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.ProcessInstanceSubscriptionRecordValue;
import java.util.stream.Stream;

public final class ProcessInstanceSubscriptionRecordStream
    extends ExporterRecordWithVariablesStream<
        ProcessInstanceSubscriptionRecordValue, ProcessInstanceSubscriptionRecordStream> {

  public ProcessInstanceSubscriptionRecordStream(
      final Stream<Record<ProcessInstanceSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ProcessInstanceSubscriptionRecordStream supply(
      final Stream<Record<ProcessInstanceSubscriptionRecordValue>> wrappedStream) {
    return new ProcessInstanceSubscriptionRecordStream(wrappedStream);
  }

  public ProcessInstanceSubscriptionRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public ProcessInstanceSubscriptionRecordStream withElementInstanceKey(
      final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public ProcessInstanceSubscriptionRecordStream withMessageName(final String messageName) {
    return valueFilter(v -> messageName.equals(v.getMessageName()));
  }
}
