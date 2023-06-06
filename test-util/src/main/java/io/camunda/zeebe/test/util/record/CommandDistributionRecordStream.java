/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
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
}
