/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.KeyGeneratorResetRecordValue;
import java.util.stream.Stream;

public final class KeyGeneratorResetRecordStream
    extends ExporterRecordStream<KeyGeneratorResetRecordValue, KeyGeneratorResetRecordStream> {

  public KeyGeneratorResetRecordStream(
      final Stream<Record<KeyGeneratorResetRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected KeyGeneratorResetRecordStream supply(
      final Stream<Record<KeyGeneratorResetRecordValue>> wrappedStream) {
    return new KeyGeneratorResetRecordStream(wrappedStream);
  }

  @Override
  public KeyGeneratorResetRecordStream withPartitionId(final int partitionId) {
    return valueFilter(v -> v.getPartitionId() == partitionId);
  }

  public KeyGeneratorResetRecordStream withNewKeyValue(final long newKeyValue) {
    return valueFilter(v -> v.getNewKeyValue() == newKeyValue);
  }
}
