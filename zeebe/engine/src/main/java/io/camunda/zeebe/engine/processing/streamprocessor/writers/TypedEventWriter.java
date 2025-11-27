/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.function.Consumer;

public interface TypedEventWriter {

  /**
   * Append a follow up event to the result builder.
   *
   * <p>Different versions of event records may be applied by different {@link
   * io.camunda.zeebe.engine.state.EventApplier EventApplier}s, leading to differing state changes.
   * This allows fixing bugs in event appliers because every event applier must produce the same
   * state changes for an event both when writing it and when replaying it, even on newer versions
   * of Zeebe.
   *
   * <p>This method always uses the latest available {@link
   * io.camunda.zeebe.engine.state.EventApplier EventApplier}. If a specific needs to be used,
   * consider using {@link #appendFollowUpEvent(long, Intent, RecordValue, int)} instead.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the record of the event
   * @throws ExceededBatchRecordSizeException if the appended event doesn't fit into the RecordBatch
   */
  void appendFollowUpEvent(long key, Intent intent, RecordValue value);

  /**
   * Append a specific version of a follow up event to the result builder.
   *
   * <p>Different versions of event records may be applied by different {@link
   * io.camunda.zeebe.engine.state.EventApplier EventApplier}s, leading to differing state changes.
   * This allows fixing bugs in event appliers because every event applier must produce the same
   * state changes for an event both when writing it and when replaying it, even on newer versions
   * of Zeebe.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the record of the event
   * @param recordVersion the version of the record of the event
   * @throws ExceededBatchRecordSizeException if the appended event doesn't fit into the RecordBatch
   */
  void appendFollowUpEvent(long key, Intent intent, RecordValue value, int recordVersion);

  /**
   * Use this to know whether you can write an event of this length.
   *
   * <p>Example:
   *
   * <pre>{@code
   * final TypedEventWriter writer;
   * // ... assign the writer
   * final TypedRecord<?> record;
   * // ... assign record
   * if (!writer.canWriteEventOfLength(record.getLength())) {
   *   // raise an incident or some such
   *   return;
   * }
   * }</pre>
   *
   * @param eventLength the length of the event that will be written
   * @return true if an event of length {@code eventLength} can be written
   */
  boolean canWriteEventOfLength(final int eventLength);

  /**
   * Validate that the provided key is valid: - it's assigned to the expected partition - it's not
   * higher than the current key
   *
   * @param key to validate
   * @param expectedPartitionId expected partition
   * @param keyGenerator to verify the key is not exceeding the highest key generated
   */
  static void validateKey(
      final long key, final int expectedPartitionId, final KeyGenerator keyGenerator) {
    if (key == -1L) {
      return;
    }
    final var decodedPartitionId = Protocol.decodePartitionId(key);
    if (decodedPartitionId == expectedPartitionId && key > keyGenerator.getCurrentKey()) {
      throw new IllegalArgumentException(
          "Expected to receive a key lesser than %d, but got %d"
              .formatted(keyGenerator.getCurrentKey(), key));
    }
  }
}
