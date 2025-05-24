/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;

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
   * io.camunda.zeebe.engine.state.EventApplier EventApplier}.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the record of the event
   * @throws ExceededBatchRecordSizeException if the appended event doesn't fit into the RecordBatch
   */
  void appendFollowUpEvent(long key, Intent intent, RecordValue value);

  /** TODO add documentation */
  void appendFollowUpEvent(
      long key, Intent intent, RecordValue value, TriggeringRecordMetadata metadata);

  /** TODO add documentation */
  default void appendFollowUpEventOnCommand(
      long key, Intent intent, RecordValue value, TypedRecord<?> triggeringCommand) {
    final boolean hasOperationReference =
        triggeringCommand.getOperationReference()
            != RecordMetadataEncoder.operationReferenceNullValue();
    if (triggeringCommand.hasRequestMetadata() || hasOperationReference) {
      appendFollowUpEvent(key, intent, value, TriggeringRecordMetadata.from(triggeringCommand));
    } else {
      appendFollowUpEvent(key, intent, value);
    }
  }

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
}
