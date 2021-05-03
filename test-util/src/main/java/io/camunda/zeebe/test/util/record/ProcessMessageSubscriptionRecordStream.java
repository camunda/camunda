/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.stream.Stream;

public final class ProcessMessageSubscriptionRecordStream
    extends ExporterRecordWithVariablesStream<
        ProcessMessageSubscriptionRecordValue, ProcessMessageSubscriptionRecordStream> {

  public ProcessMessageSubscriptionRecordStream(
      final Stream<Record<ProcessMessageSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ProcessMessageSubscriptionRecordStream supply(
      final Stream<Record<ProcessMessageSubscriptionRecordValue>> wrappedStream) {
    return new ProcessMessageSubscriptionRecordStream(wrappedStream);
  }

  public ProcessMessageSubscriptionRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public ProcessMessageSubscriptionRecordStream withElementInstanceKey(
      final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public ProcessMessageSubscriptionRecordStream withMessageName(final String messageName) {
    return valueFilter(v -> messageName.equals(v.getMessageName()));
  }
}
