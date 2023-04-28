/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import java.util.function.Supplier;

final class ResultBuilderBackedTypedCommandWriter extends AbstractResultBuilderBackedWriter
    implements TypedCommandWriter {

  ResultBuilderBackedTypedCommandWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderSupplier) {
    super(resultBuilderSupplier);
  }

  @Override
  public void appendNewCommand(final Intent intent, final RecordValue value) {
    appendRecord(-1, intent, value);
  }

  @Override
  public void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {
    appendRecord(key, intent, value);
  }

  @Override
  public boolean canWriteCommandOfLength(final int commandLength) {
    return resultBuilder().canWriteEventOfLength(commandLength);
  }

  private void appendRecord(final long key, final Intent intent, final RecordValue value) {
    resultBuilder()
        .appendRecord(key, RecordType.COMMAND, intent, RejectionType.NULL_VAL, "", value);
  }
}
