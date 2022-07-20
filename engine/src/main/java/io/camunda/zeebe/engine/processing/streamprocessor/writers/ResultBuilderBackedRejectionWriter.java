/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.function.Supplier;

final class ResultBuilderBackedRejectionWriter extends AbstractResultBuilderBackedWriter
    implements TypedRejectionWriter {

  ResultBuilderBackedRejectionWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderSupplier) {
    super(resultBuilderSupplier);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends RecordValue> command,
      final RejectionType rejectionType,
      final String reason) {
    resultBuilder()
        .appendRecord(
            command.getKey(),
            RecordType.COMMAND_REJECTION,
            command.getIntent(),
            rejectionType,
            reason,
            command.getValue());
  }
}
