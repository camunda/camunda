/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.CompensationSubscriptionRecordValue;
import java.util.stream.Stream;

public class CompensationSubscriptionRecordStream
    extends ExporterRecordStream<
        CompensationSubscriptionRecordValue, CompensationSubscriptionRecordStream> {

  public CompensationSubscriptionRecordStream(
      final Stream<Record<CompensationSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected CompensationSubscriptionRecordStream supply(
      final Stream<Record<CompensationSubscriptionRecordValue>> wrappedStream) {
    return new CompensationSubscriptionRecordStream(wrappedStream);
  }

  public CompensationSubscriptionRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }
}
