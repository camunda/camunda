/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.function.Supplier;

final class ResultBuilderBackedTypedCommandWriter implements TypedCommandWriter {

  /* supplier for result builder, result builder must not be cached as the concrete result builder is a
  request scoped object, i.e. it is a new one for each record that is being processed*/
  private final Supplier<ProcessingResultBuilder> resultBuilderSupplier;

  ResultBuilderBackedTypedCommandWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderSupplier) {
    this.resultBuilderSupplier = resultBuilderSupplier;
  }

  @Override
  public void appendNewCommand(final Intent intent, final RecordValue value) {
    appendRecord(-1, intent, value);
  }

  @Override
  public void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {
    appendRecord(key, intent, value);
  }

  private void appendRecord(final long key, final Intent intent, final RecordValue value) {
    resultBuilder()
        .appendRecord(key, RecordType.COMMAND, intent, RejectionType.NULL_VAL, "", value);
  }

  private ProcessingResultBuilder resultBuilder() {
    return resultBuilderSupplier.get();
  }
}
