/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
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
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND_REJECTION)
            .intent(command.getIntent())
            .authorization(new AuthInfo().setClaims(command.getAuthorizations()))
            .rejectionType(rejectionType)
            .rejectionReason(reason)
            .operationReference(command.getOperationReference());
    resultBuilder().appendRecord(command.getKey(), command.getValue(), metadata);
  }
}
