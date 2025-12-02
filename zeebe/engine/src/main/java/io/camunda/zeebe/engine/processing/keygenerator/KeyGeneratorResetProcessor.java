/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.keygenerator;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.keygenerator.KeyGeneratorResetRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.KeyGeneratorResetIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Processes commands to reset the key generator for a partition.
 *
 * <p>This is an administrative operation that allows setting a new starting point for key
 * generation within a partition. The key generator will also accept new values that are lower than
 * the current key. This can lead to collision with existing or previously completed processes. This
 * operation must be used with caution and is intended to be used only to recover the cluster from
 * specific failures.
 */
public final class KeyGeneratorResetProcessor
    implements TypedRecordProcessor<KeyGeneratorResetRecord> {

  private static final String INVALID_PARTITION_ERROR_MESSAGE =
      "Expected to reset key generator for partition %d, but this command was sent to partition %d";
  private static final String INVALID_KEY_PARTITION_ERROR_MESSAGE =
      "Expected new key value to belong to partition %d, but it belongs to partition %d";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final int partitionId;

  public KeyGeneratorResetProcessor(final Writers writers, final int partitionId) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.partitionId = partitionId;
  }

  @Override
  public void processRecord(final TypedRecord<KeyGeneratorResetRecord> command) {
    // For now, we skip authorization. But can be added later when this operation is supported via
    // api

    final var record = command.getValue();

    // Validate partition ID matches current partition
    if (record.getPartitionId() != partitionId) {
      final var message =
          INVALID_PARTITION_ERROR_MESSAGE.formatted(record.getPartitionId(), partitionId);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, message);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, message);
      return;
    }

    // Validate the new key value belongs to this partition
    final var keyPartitionId = Protocol.decodePartitionId(record.getNewKeyValue());
    if (keyPartitionId != partitionId) {
      final var message =
          INVALID_KEY_PARTITION_ERROR_MESSAGE.formatted(partitionId, keyPartitionId);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, message);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, message);
      return;
    }

    // Use -1 as key as we don't need a new key for this specific event
    stateWriter.appendFollowUpEvent(-1, KeyGeneratorResetIntent.RESET_APPLIED, record);

    // Send response if this was a request
    if (command.hasRequestMetadata()) {
      responseWriter.writeEventOnCommand(
          -1, KeyGeneratorResetIntent.RESET_APPLIED, record, command);
    }
  }
}
