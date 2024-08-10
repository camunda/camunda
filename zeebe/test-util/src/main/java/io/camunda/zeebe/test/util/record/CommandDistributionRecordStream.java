/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import java.util.stream.Stream;

public class CommandDistributionRecordStream
    extends ExporterRecordStream<CommandDistributionRecordValue, CommandDistributionRecordStream> {

  public CommandDistributionRecordStream(
      final Stream<Record<CommandDistributionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected CommandDistributionRecordStream supply(
      final Stream<Record<CommandDistributionRecordValue>> wrappedStream) {
    return new CommandDistributionRecordStream(wrappedStream);
  }

  public CommandDistributionRecordStream withDistributionPartitionId(final int partitionId) {
    return valueFilter(v -> v.getPartitionId() == partitionId);
  }

  public CommandDistributionRecordStream withDistributionIntent(final Intent intent) {
    return valueFilter(v -> v.getIntent() == intent);
  }
}
