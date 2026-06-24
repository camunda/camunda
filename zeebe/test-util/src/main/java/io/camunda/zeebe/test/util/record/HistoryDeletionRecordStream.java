/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.stream.Stream;

public class HistoryDeletionRecordStream
    extends ExporterRecordStream<HistoryDeletionRecordValue, HistoryDeletionRecordStream> {

  public HistoryDeletionRecordStream(
      final Stream<Record<HistoryDeletionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected HistoryDeletionRecordStream supply(
      final Stream<Record<HistoryDeletionRecordValue>> wrappedStream) {
    return new HistoryDeletionRecordStream(wrappedStream);
  }

  public HistoryDeletionRecordStream withResourceKey(final long resourceKey) {
    return valueFilter(v -> v.getResourceKey() == resourceKey);
  }

  public HistoryDeletionRecordStream withResourceType(final HistoryDeletionType resourceType) {
    return valueFilter(v -> v.getResourceType() == resourceType);
  }
}
