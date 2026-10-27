/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import java.util.function.Consumer;

public interface TypedEventWriter {

  /**
   * Append a follow-up event to the result builder.
   *
   * <p>Different versions of event records may be applied by different {@link
   * io.camunda.zeebe.engine.state.EventApplier EventApplier}s, leading to differing state changes.
   * This allows fixing bugs in event appliers because every event applier must produce the same
   * state changes for an event both when writing it and when replaying it, even on newer versions
   * of Zeebe.
   *
   * <p>This method always uses the latest available {@link
   * io.camunda.zeebe.engine.state.EventApplier}. If a specific version needs to be used, consider
   * using {@link #appendFollowUpEvent(long, Intent, RecordValue, int)} instead.
   *
   * <p>For advanced use cases requiring enriched metadata such as {@code operationReference},
   * consider using {@link #appendFollowUpEvent(long, Intent, RecordValue, FollowUpEventMetadata)}.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the record of the event
   * @throws ExceededBatchRecordSizeException if the appended event doesn't fit into the RecordBatch
   */
  void appendFollowUpEvent(long key, Intent intent, RecordValue value);

  /**
   * Append a specific version of a follow-up event to the result builder.
   *
   * <p>Different versions of event records may be applied by different {@link
   * io.camunda.zeebe.engine.state.EventApplier}s, leading to differing state changes. This allows
   * fixing bugs in event appliers because every event applier must produce the same state changes
   * for an event both when writing it and when replaying it, even on newer versions of Zeebe.
   *
   * <p>For advanced use cases requiring additional metadata such as {@code operationReference},
   * consider using {@link #appendFollowUpEvent(long, Intent, RecordValue, FollowUpEventMetadata)}.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the record of the event
   * @param recordVersion the version of the record of the event
   * @throws ExceededBatchRecordSizeException if the appended event doesn't fit into the RecordBatch
   */
  void appendFollowUpEvent(long key, Intent intent, RecordValue value, int recordVersion);

  /**
   * Append a follow-up event with additional metadata (like operation reference and/or record
   * version).
   *
   * <p>This variant allows writing enriched events with a specific {@code operationReference} that
   * will be used to correlate event with the client operations.
   *
   * <p>If the record version isn't explicitly set in metadata, the latest version from the {@link
   * io.camunda.zeebe.engine.state.EventApplier} will be used.
   *
   * <p>This method is intended for advanced use cases requiring enriched metadata for correlation
   * or tracking purposes. For typical usage, consider using {@link #appendFollowUpEvent(long,
   * Intent, RecordValue)}.
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the value of the record
   * @param metadata metadata for the follow-up event
   * @throws ExceededBatchRecordSizeException if the appended event doesn't fit into the RecordBatch
   */
  void appendFollowUpEvent(
      long key, Intent intent, RecordValue value, FollowUpEventMetadata metadata);

  /**
   * A convenient form of {@link #appendFollowUpEvent(long, Intent, RecordValue,
   * FollowUpEventMetadata)}.
   *
   * <p>Allows configuring {@link FollowUpEventMetadata} using a builder-style lambda.
   *
   * <p>Example:
   *
   * <pre>{@code
   * writer.appendFollowUpEvent(
   *     key, intent, value, m -> m.operationReference(1234L));
   * }</pre>
   *
   * <p>Equivalent to:
   *
   * <pre>{@code
   * writer.appendFollowUpEvent(
   *     key, intent, value,
   *     FollowUpEventMetadata.builder().operationReference(1234L).build());
   * }</pre>
   *
   * @param key the key of the event
   * @param intent the intent of the event
   * @param value the value of the event
   * @param builderConsumer lambda to configure follow-up event metadata
   * @throws ExceededBatchRecordSizeException if the event exceeds batch size
   */
  default void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final RecordValue value,
      final Consumer<FollowUpEventMetadata.Builder> builderConsumer) {
    final var builder = FollowUpEventMetadata.builder();
    builderConsumer.accept(builder);
    appendFollowUpEvent(key, intent, value, builder.build());
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
