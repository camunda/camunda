/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;
import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
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
  public void appendFollowUpCommand(
      final long key,
      final Intent intent,
      final RecordValue value,
      final CommandMetadata metadata) {
    appendRecord(key, intent, value, metadata);
  }

  @Override
  public boolean canWriteCommandOfLength(final int commandLength) {
    return resultBuilder().canWriteEventOfLength(commandLength);
  }

  private void appendRecord(final long key, final Intent intent, final RecordValue value) {
    appendRecord(
        key,
        intent,
        value,
        CommandMetadata.of(
            b ->
                b.operationReference(operationReferenceNullValue())
                    .batchOperationReference(batchOperationReferenceNullValue())
                    .claims(null)));
  }

  private void appendRecord(
      final long key,
      final Intent intent,
      final RecordValue value,
      final CommandMetadata commandMetadata) {
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .operationReference(commandMetadata.operationReference())
            .batchOperationReference(commandMetadata.batchOperationReference());

    if (commandMetadata.claims() != null) {
      metadata.authorization(new AuthInfo().setClaims(commandMetadata.claims()));
    }

    resultBuilder().appendRecord(key, value, metadata);
  }
}
